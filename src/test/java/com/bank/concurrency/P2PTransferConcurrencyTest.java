package com.bank.concurrency;

import com.bank.dto.request.TransferRequest;
import com.bank.entity.User;
import com.bank.entity.Wallet;
import com.bank.enums.UserStatus;
import com.bank.enums.WalletStatus;
import com.bank.enums.WalletType;
import com.bank.repository.UserRepository;
import com.bank.repository.WalletRepository;
import com.bank.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class P2PTransferConcurrencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    private Wallet walletA;
    private Wallet walletB;

    @BeforeEach
    void setUp() {
        User userA = userRepository.findByUsername("userA").orElseGet(() ->
                userRepository.save(User.builder().username("userA").email("usera@bank.com").passwordHash("pwd").fullName("User A").status(UserStatus.ACTIVE).build())
        );

        User userB = userRepository.findByUsername("userB").orElseGet(() ->
                userRepository.save(User.builder().username("userB").email("userb@bank.com").passwordHash("pwd").fullName("User B").status(UserStatus.ACTIVE).build())
        );

        walletA = walletRepository.findByWalletNumber("W_TEST_A").orElseGet(() ->
                walletRepository.save(Wallet.builder().walletNumber("W_TEST_A").user(userA).walletType(WalletType.MAIN).balance(new BigDecimal("10000000.0000")).currency("VND").status(WalletStatus.ACTIVE).build())
        );

        walletB = walletRepository.findByWalletNumber("W_TEST_B").orElseGet(() ->
                walletRepository.save(Wallet.builder().walletNumber("W_TEST_B").user(userB).walletType(WalletType.MAIN).balance(new BigDecimal("0.0000")).currency("VND").status(WalletStatus.ACTIVE).build())
        );

        walletA.setBalance(new BigDecimal("10000000.0000"));
        walletB.setBalance(new BigDecimal("0.0000"));
        walletRepository.save(walletA);
        walletRepository.save(walletB);
    }

    @Test
    @DisplayName("Stress Test: 100 luồng chuyển tiền đồng thời từ Ví A sang Ví B - Đảm bảo Atomic và không mất tiền")
    void testConcurrentTransferNoMoneyLost() throws InterruptedException {
        int numberOfThreads = 100;
        BigDecimal transferAmount = new BigDecimal("10000.0000");

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    transferService.transferP2P(TransferRequest.builder()
                            .sourceWalletNumber("W_TEST_A")
                            .destinationWalletNumber("W_TEST_B")
                            .amount(transferAmount)
                            .idempotencyKey(UUID.randomUUID().toString())
                            .description("Stress test concurrent transfer")
                            .build());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;
        double tps = (double) numberOfThreads / (duration / 1000.0);

        System.out.println("========== KET QUA STRESS TEST ==========");
        System.out.println("Tong so giao dich: " + numberOfThreads);
        System.out.println("Thanh cong: " + successCount.get());
        System.out.println("That bai: " + failCount.get());
        System.out.println("Thoi gian thuc thi: " + duration + " ms");
        System.out.println("TPS uoc tinh: " + String.format("%.2f", tps) + " req/sec");
        System.out.println("=========================================");

        Wallet finalWalletA = walletRepository.findByWalletNumber("W_TEST_A").orElseThrow();
        Wallet finalWalletB = walletRepository.findByWalletNumber("W_TEST_B").orElseThrow();

        BigDecimal expectedDeduction = transferAmount.multiply(new BigDecimal(successCount.get()));
        assertEquals(0, new BigDecimal("10000000.0000").subtract(expectedDeduction).compareTo(finalWalletA.getBalance()),
                "Số dư ví A sau cùng phải khớp chính xác");
        assertEquals(0, expectedDeduction.compareTo(finalWalletB.getBalance()),
                "Số dư ví B sau cùng phải nhận đủ chính xác");
    }
}
