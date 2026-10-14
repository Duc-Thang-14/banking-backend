package com.bank.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BeneficiaryRequest {

    @NotBlank(message = "Số ví người thụ hưởng không được để trống")
    private String beneficiaryWalletNumber;

    @NotBlank(message = "Tên gợi nhớ không được để trống")
    private String nickname;
}
