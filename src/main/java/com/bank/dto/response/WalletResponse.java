package com.bank.dto.response;

import com.bank.entity.Wallet;
import com.bank.enums.WalletStatus;
import com.bank.enums.WalletType;
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
public class WalletResponse {

    private String walletNumber;
    private Long userId;
    private String username;
    private WalletType walletType;
    private BigDecimal balance;
    private String currency;
    private WalletStatus status;
    private Instant createdAt;

    public static WalletResponse fromEntity(Wallet wallet) {
        return WalletResponse.builder()
                .walletNumber(wallet.getWalletNumber())
                .userId(wallet.getUser() != null ? wallet.getUser().getId() : null)
                .username(wallet.getUser() != null ? wallet.getUser().getUsername() : null)
                .walletType(wallet.getWalletType())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus())
                .createdAt(wallet.getCreatedAt())
                .build();
    }
}
