package com.bank.service;

import com.bank.dto.request.TopUpRequest;
import com.bank.dto.request.WithdrawRequest;
import com.bank.dto.response.TransactionHistoryResponse;
import com.bank.dto.response.WalletResponse;
import com.bank.entity.LedgerEntry;
import com.bank.entity.Transaction;
import com.bank.entity.Wallet;
import com.bank.enums.LedgerEntryType;
import com.bank.enums.TransactionStatus;
import com.bank.enums.TransactionType;
import com.bank.enums.WalletStatus;
import com.bank.exception.AppException;
import com.bank.exception.ErrorCode;
import com.bank.repository.LedgerEntryRepository;
import com.bank.repository.TransactionRepository;
import com.bank.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OtpService otpService;

    @Transactional(readOnly = true)
    public List<WalletResponse> getWalletsByUserId(Long userId) {
        return walletRepository.findByUserId(userId).stream()
                .map(WalletResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletByNumber(String walletNumber) {
        Wallet wallet = walletRepository.findByWalletNumber(walletNumber)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND, "Không tìm thấy ví: " + walletNumber));
        return WalletResponse.fromEntity(wallet);
    }

    @Transactional
    public WalletResponse topUp(TopUpRequest request) {
        Wallet wallet = walletRepository.findByWalletNumberWithPessimisticLock(request.getWalletNumber())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND, "Không tìm thấy ví: " + request.getWalletNumber()));

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new AppException(ErrorCode.WALLET_LOCKED);
        }

        BigDecimal beforeBalance = wallet.getBalance();
        BigDecimal afterBalance = beforeBalance.add(request.getAmount());
        wallet.setBalance(afterBalance);
        walletRepository.save(wallet);

        // 1. Tạo Transaction nạp tiền
        Transaction transaction = Transaction.builder()
                .transactionReference("TOPUP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase())
                .destinationWallet(wallet)
                .amount(request.getAmount())
                .type(TransactionType.TOP_UP)
                .status(TransactionStatus.SUCCESS)
                .currency(wallet.getCurrency())
                .build();
        transaction = transactionRepository.save(transaction);

        // 2. Ghi sổ kép (Ledger Entry: CREDIT cho ví người dùng)
        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .wallet(wallet)
                .entryType(LedgerEntryType.CREDIT)
                .amount(request.getAmount())
                .balanceBefore(beforeBalance)
                .balanceAfter(afterBalance)
                .currency(wallet.getCurrency())
                .description("Nạp tiền vào tài khoản")
                .build();
        ledgerEntryRepository.save(creditEntry);

        return WalletResponse.fromEntity(wallet);
    }

    @Transactional(readOnly = true)
    public List<TransactionHistoryResponse> getTransactionHistory(String walletNumber) {
        List<Transaction> list = transactionRepository.findRecentByWalletNumber(walletNumber);
        return list.stream().map(tx -> {
            boolean isOutgoing = tx.getSourceWallet() != null && walletNumber.equals(tx.getSourceWallet().getWalletNumber());
            return TransactionHistoryResponse.builder()
                    .transactionReference(tx.getTransactionReference())
                    .sourceWalletNumber(tx.getSourceWallet() != null ? tx.getSourceWallet().getWalletNumber() : null)
                    .destinationWalletNumber(tx.getDestinationWallet() != null ? tx.getDestinationWallet().getWalletNumber() : null)
                    .amount(tx.getAmount())
                    .fee(tx.getFee())
                    .currency(tx.getCurrency())
                    .type(tx.getType())
                    .status(tx.getStatus())
                    .direction(isOutgoing ? "OUT" : "IN")
                    .createdAt(tx.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public com.bank.dto.response.PageResponse<TransactionHistoryResponse> searchTransactions(
            String walletNumber,
            TransactionType type,
            org.springframework.data.domain.Pageable pageable
    ) {
        org.springframework.data.domain.Page<Transaction> page = transactionRepository.findFilteredByWalletNumber(walletNumber, type, pageable);
        org.springframework.data.domain.Page<TransactionHistoryResponse> dtoPage = page.map(tx -> {
            boolean isOutgoing = tx.getSourceWallet() != null && walletNumber.equals(tx.getSourceWallet().getWalletNumber());
            return TransactionHistoryResponse.builder()
                    .transactionReference(tx.getTransactionReference())
                    .sourceWalletNumber(tx.getSourceWallet() != null ? tx.getSourceWallet().getWalletNumber() : null)
                    .destinationWalletNumber(tx.getDestinationWallet() != null ? tx.getDestinationWallet().getWalletNumber() : null)
                    .amount(tx.getAmount())
                    .fee(tx.getFee())
                    .currency(tx.getCurrency())
                    .type(tx.getType())
                    .status(tx.getStatus())
                    .direction(isOutgoing ? "OUT" : "IN")
                    .createdAt(tx.getCreatedAt())
                    .build();
        });
        return com.bank.dto.response.PageResponse.of(dtoPage);
    }

    @Transactional
    public WalletResponse withdraw(WithdrawRequest request) {
        // Kiểm tra xác thực OTP (nếu có yêu cầu từ giao diện)
        if (request.getOtp() != null && !request.getOtp().isBlank()) {
            otpService.verifyOtp("WITHDRAW", request.getWalletNumber(), request.getOtp());
        }

        Wallet wallet = walletRepository.findByWalletNumberWithPessimisticLock(request.getWalletNumber())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new AppException(ErrorCode.WALLET_LOCKED);
        }

        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        BigDecimal beforeBalance = wallet.getBalance();
        BigDecimal afterBalance = beforeBalance.subtract(request.getAmount());
        wallet.setBalance(afterBalance);
        walletRepository.save(wallet);

        // 1. Tạo Transaction rút tiền
        String txRef = "WDR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        Transaction transaction = Transaction.builder()
                .transactionReference(txRef)
                .sourceWallet(wallet)
                .amount(request.getAmount())
                .fee(BigDecimal.ZERO)
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .currency(wallet.getCurrency())
                .build();
        transaction = transactionRepository.save(transaction);

        // 2. Ghi sổ kép Double-entry (DEBIT ví)
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .wallet(wallet)
                .entryType(LedgerEntryType.DEBIT)
                .amount(request.getAmount())
                .balanceBefore(beforeBalance)
                .balanceAfter(afterBalance)
                .currency(wallet.getCurrency())
                .description("Rút tiền về " + request.getBankName() + " - STK: " + request.getBankAccountNumber() + " (" + request.getBankAccountName() + ")")
                .build();
        ledgerEntryRepository.save(debitEntry);

        return WalletResponse.fromEntity(wallet);
    }
}
