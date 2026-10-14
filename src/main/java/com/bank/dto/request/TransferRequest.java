package com.bank.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Số ví gửi không được để trống")
    private String sourceWalletNumber;

    @NotBlank(message = "Số ví nhận không được để trống")
    private String destinationWalletNumber;

    @NotNull(message = "Số tiền không được để trống")
    @DecimalMin(value = "1000.00", message = "Số tiền chuyển tối thiểu là 1,000 VND")
    private BigDecimal amount;

    private String description;

    private String idempotencyKey;

    private String otp;
}
