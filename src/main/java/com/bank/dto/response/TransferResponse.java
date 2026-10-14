package com.bank.dto.response;

import com.bank.enums.TransactionStatus;
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
public class TransferResponse {

    private String transactionReference;
    private String sourceWalletNumber;
    private String destinationWalletNumber;
    private BigDecimal amount;
    private BigDecimal fee;
    private String currency;
    private TransactionStatus status;
    private String description;
    private Instant createdAt;
}
