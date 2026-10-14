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
public class TopUpRequest {

    @NotBlank(message = "Số ví nạp không được để trống")
    private String walletNumber;

    @NotNull(message = "Số tiền nạp không được để trống")
    @DecimalMin(value = "10000.00", message = "Số tiền nạp tối thiểu là 10,000 VND")
    private BigDecimal amount;
}
