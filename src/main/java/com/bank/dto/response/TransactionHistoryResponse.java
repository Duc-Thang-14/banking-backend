package com.bank.dto.response;

import com.bank.enums.TransactionStatus;
import com.bank.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHistoryResponse {
    private String transactionReference;
    private String sourceWalletNumber;
    private String destinationWalletNumber;
    private BigDecimal amount;
    private BigDecimal fee;
    private String currency;
    private TransactionType type;
    private TransactionStatus status;
    private String direction; // "IN" hoặc "OUT"
    private Instant createdAt;
}
