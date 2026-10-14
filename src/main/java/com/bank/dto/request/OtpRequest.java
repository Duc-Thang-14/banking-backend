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
public class OtpRequest {

    @NotBlank(message = "Hành động yêu cầu OTP không được để trống (ví dụ: TRANSFER, WITHDRAW)")
    private String action;

    @NotBlank(message = "Đối tượng yêu cầu OTP không được để trống (ví dụ số ví)")
    private String target;
}
