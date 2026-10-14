package com.bank.dto.response;

import com.bank.entity.Beneficiary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BeneficiaryResponse {
    private Long id;
    private String beneficiaryWalletNumber;
    private String nickname;
    private Instant createdAt;

    public static BeneficiaryResponse fromEntity(Beneficiary beneficiary) {
        return BeneficiaryResponse.builder()
                .id(beneficiary.getId())
                .beneficiaryWalletNumber(beneficiary.getBeneficiaryWalletNumber())
                .nickname(beneficiary.getNickname())
                .createdAt(beneficiary.getCreatedAt())
                .build();
    }
}
