package com.bank.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpResponse {
    private String message;
    private long expiresInSeconds;
    // Để môi trường dev / phỏng vấn demo thuận tiện
    private String debugOtp;
}
