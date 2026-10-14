package com.bank.service;

import com.bank.dto.request.TransferRequest;
import com.bank.dto.response.TransferResponse;
import com.bank.entity.LedgerEntry;
import com.bank.entity.Transaction;
import com.bank.entity.Wallet;
import com.bank.enums.LedgerEntryType;
import com.bank.enums.TransactionStatus;
import com.bank.enums.TransactionType;
import com.bank.enums.WalletStatus;
import com.bank.event.TransactionEvent;
import com.bank.exception.AppException;
import com.bank.exception.ErrorCode;
import com.bank.kafka.TransactionEventProducer;
import com.bank.repository.LedgerEntryRepository;
import com.bank.repository.TransactionRepository;
import com.bank.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final TransactionEventProducer transactionEventProducer;
    private final OtpService otpService;

    private static final BigDecimal MAX_PER_TRANSACTION = new BigDecimal("50000000");
    private static final BigDecimal MAX_DAILY_LIMIT = new BigDecimal("100000000");

    public TransferResponse transferP2P(TransferRequest request) {
        if (request.getSourceWalletNumber().equals(request.getDestinationWalletNumber())) {
            throw new AppException(ErrorCode.SAME_WALLET_TRANSFER);
        }

        // Kiểm tra xác thực OTP (nếu có yêu cầu từ giao diện)
        if (request.getOtp() != null && !request.getOtp().isBlank()) {
            otpService.verifyOtp("TRANSFER", request.getSourceWalletNumber(), request.getOtp());
        }

        // 0. Kiểm tra hạn mức chuyển tiền (Hạn mức lần & Hạn mức ngày Redis)
        checkAndRecordDailyLimit(request.getSourceWalletNumber(), request.getAmount());

        // 1. Kiểm tra Idempotency Key chống bấm lặp (Double-Submit)
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existingTx.isPresent()) {
                Transaction tx = existingTx.get();
                log.info("Idempotent request detected for key: {}", idempotencyKey);
                return TransferResponse.builder()
                        .transactionReference(tx.getTransactionReference())
                        .sourceWalletNumber(tx.getSourceWallet().getWalletNumber())
                        .destinationWalletNumber(tx.getDestinationWallet().getWalletNumber())
                        .amount(tx.getAmount())
                        .fee(tx.getFee())
                        .currency(tx.getCurrency())
                        .status(tx.getStatus())
                        .createdAt(tx.getCreatedAt())
                        .build();
            }

            // Đánh dấu idempotency đang xử lý trong Redis với TTL 2 phút
            String redisIdempKey = "idemp:" + idempotencyKey;
            Boolean isNewRequest = redisTemplate.opsForValue().setIfAbsent(redisIdempKey, "PROCESSING", Duration.ofMinutes(2));
            if (Boolean.FALSE.equals(isNewRequest)) {
                throw new AppException(ErrorCode.TRANSACTION_PROCESSING);
            }
        }

        // 2. Phân tán Lock: Khóa trên Redis trước khi vào DB
        String firstLockKey = request.getSourceWalletNumber().compareTo(request.getDestinationWalletNumber()) < 0
                ? "lock:wallet:" + request.getSourceWalletNumber()
                : "lock:wallet:" + request.getDestinationWalletNumber();
        String secondLockKey = request.getSourceWalletNumber().compareTo(request.getDestinationWalletNumber()) < 0
                ? "lock:wallet:" + request.getDestinationWalletNumber()
                : "lock:wallet:" + request.getSourceWalletNumber();

        RLock lock1 = redissonClient.getLock(firstLockKey);
        RLock lock2 = redissonClient.getLock(secondLockKey);

        boolean acquired1 = false;
        boolean acquired2 = false;

        TransferResponse response;
        try {
            acquired1 = lock1.tryLock(5, 10, TimeUnit.SECONDS);
            acquired2 = lock2.tryLock(5, 10, TimeUnit.SECONDS);

            if (!acquired1 || !acquired2) {
                throw new AppException(ErrorCode.SYSTEM_BUSY);
            }

            // 3. Thực thi nghiệp vụ chuyển tiền an toàn trong DB Transaction qua TransactionTemplate
            response = transactionTemplate.execute(status -> executeTransferBusiness(request));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Tiến trình bị gián đoạn khi đang chờ khóa phân tán", e);
        } finally {
            if (acquired2 && lock2.isHeldByCurrentThread()) {
                lock2.unlock();
            }
            if (acquired1 && lock1.isHeldByCurrentThread()) {
                lock1.unlock();
            }
        }

        // 4. Sau khi Transaction DB đã Commit thành công -> Bắn event vào Kafka bất đồng bộ
        if (response != null && response.getStatus() == TransactionStatus.SUCCESS) {
            try {
                transactionEventProducer.publishTransactionEvent(
                        TransactionEvent.builder()
                                .transactionReference(response.getTransactionReference())
                                .sourceWalletNumber(response.getSourceWalletNumber())
                                .destinationWalletNumber(response.getDestinationWalletNumber())
                                .amount(response.getAmount())
                                .currency(response.getCurrency())
                                .type(TransactionType.P2P_TRANSFER)
                                .status(response.getStatus())
                                .description(response.getDescription())
                                .timestamp(Instant.now())
                                .build()
                );
            } catch (Exception e) {
                log.error("Không thể gửi event lên Kafka cho giao dịch {}: {}", response.getTransactionReference(), e.getMessage());
            }
        }

        return response;
    }

    private TransferResponse executeTransferBusiness(TransferRequest request) {
        Wallet sourceMeta = walletRepository.findByWalletNumber(request.getSourceWalletNumber())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND, "Ví nguồn không tồn tại: " + request.getSourceWalletNumber()));
        Wallet destMeta = walletRepository.findByWalletNumber(request.getDestinationWalletNumber())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND, "Ví đích không tồn tại: " + request.getDestinationWalletNumber()));

        Wallet sourceWallet;
        Wallet destWallet;
        if (sourceMeta.getId() < destMeta.getId()) {
            sourceWallet = walletRepository.findByIdWithPessimisticLock(sourceMeta.getId()).orElseThrow();
            destWallet = walletRepository.findByIdWithPessimisticLock(destMeta.getId()).orElseThrow();
        } else {
            destWallet = walletRepository.findByIdWithPessimisticLock(destMeta.getId()).orElseThrow();
            sourceWallet = walletRepository.findByIdWithPessimisticLock(sourceMeta.getId()).orElseThrow();
        }

        if (sourceWallet.getStatus() != WalletStatus.ACTIVE || destWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new AppException(ErrorCode.WALLET_LOCKED, "Một trong hai ví đang không ở trạng thái hoạt động");
        }

        if (sourceWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        if (!sourceWallet.getCurrency().equalsIgnoreCase(destWallet.getCurrency())) {
            throw new AppException(ErrorCode.CURRENCY_MISMATCH);
        }

        BigDecimal sourceBefore = sourceWallet.getBalance();
        BigDecimal sourceAfter = sourceBefore.subtract(request.getAmount());
        sourceWallet.setBalance(sourceAfter);

        BigDecimal destBefore = destWallet.getBalance();
        BigDecimal destAfter = destBefore.add(request.getAmount());
        destWallet.setBalance(destAfter);

        walletRepository.save(sourceWallet);
        walletRepository.save(destWallet);

        String txRef = "TRF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        Transaction transaction = Transaction.builder()
                .transactionReference(txRef)
                .idempotencyKey(request.getIdempotencyKey())
                .sourceWallet(sourceWallet)
                .destinationWallet(destWallet)
                .amount(request.getAmount())
                .fee(BigDecimal.ZERO)
                .currency(sourceWallet.getCurrency())
                .type(TransactionType.P2P_TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .build();
        transaction = transactionRepository.save(transaction);

        // Ghi sổ kép Double-Entry Bookkeeping
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .wallet(sourceWallet)
                .entryType(LedgerEntryType.DEBIT)
                .amount(request.getAmount())
                .balanceBefore(sourceBefore)
                .balanceAfter(sourceAfter)
                .currency(sourceWallet.getCurrency())
                .description("Chuyển tiền tới ví " + destWallet.getWalletNumber() + ": " + (request.getDescription() != null ? request.getDescription() : ""))
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .wallet(destWallet)
                .entryType(LedgerEntryType.CREDIT)
                .amount(request.getAmount())
                .balanceBefore(destBefore)
                .balanceAfter(destAfter)
                .currency(destWallet.getCurrency())
                .description("Nhận tiền từ ví " + sourceWallet.getWalletNumber() + ": " + (request.getDescription() != null ? request.getDescription() : ""))
                .build();

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        return TransferResponse.builder()
                .transactionReference(transaction.getTransactionReference())
                .sourceWalletNumber(sourceWallet.getWalletNumber())
                .destinationWalletNumber(destWallet.getWalletNumber())
                .amount(request.getAmount())
                .fee(BigDecimal.ZERO)
                .currency(sourceWallet.getCurrency())
                .status(TransactionStatus.SUCCESS)
                .description(request.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private void checkAndRecordDailyLimit(String walletNumber, BigDecimal amount) {
        if (amount.compareTo(MAX_PER_TRANSACTION) > 0) {
            throw new AppException(ErrorCode.TRANSACTION_LIMIT_EXCEEDED);
        }

        String today = java.time.LocalDate.now().toString();
        String limitKey = "limit:daily:" + walletNumber + ":" + today;

        String currentDailyStr = redisTemplate.opsForValue().get(limitKey);
        BigDecimal currentDaily = currentDailyStr != null ? new BigDecimal(currentDailyStr) : BigDecimal.ZERO;

        if (currentDaily.add(amount).compareTo(MAX_DAILY_LIMIT) > 0) {
            throw new AppException(ErrorCode.DAILY_LIMIT_EXCEEDED);
        }

        redisTemplate.opsForValue().increment(limitKey, amount.doubleValue());
        redisTemplate.expire(limitKey, Duration.ofHours(24));
    }
}
