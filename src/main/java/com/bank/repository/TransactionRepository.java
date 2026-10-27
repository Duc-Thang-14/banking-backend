package com.bank.repository;

import com.bank.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.bank.enums.TransactionType;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionReference(String transactionReference);
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM Transaction t " +
           "LEFT JOIN t.sourceWallet sw " +
           "LEFT JOIN t.destinationWallet dw " +
           "WHERE (sw.walletNumber = :walletNumber OR dw.walletNumber = :walletNumber) " +
           "ORDER BY t.createdAt DESC")
    List<Transaction> findRecentByWalletNumber(@Param("walletNumber") String walletNumber);

    @Query("SELECT t FROM Transaction t " +
           "LEFT JOIN t.sourceWallet sw " +
           "LEFT JOIN t.destinationWallet dw " +
           "WHERE (sw.walletNumber = :walletNumber OR dw.walletNumber = :walletNumber) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "ORDER BY t.createdAt DESC")
    Page<Transaction> findFilteredByWalletNumber(
            @Param("walletNumber") String walletNumber,
            @Param("type") TransactionType type,
            Pageable pageable
    );
}

