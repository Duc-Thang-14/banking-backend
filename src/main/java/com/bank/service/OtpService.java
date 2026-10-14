package com.bank.service;

import com.bank.dto.request.OtpRequest;
import com.bank.dto.response.OtpResponse;
import com.bank.exception.AppException;
import com.bank.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final long OTP_TTL_SECONDS = 120; // 2 phút
    private static final int MAX_OTP_ATTEMPTS = 3;

    public OtpResponse generateOtp(OtpRequest request) {
        String action = request.getAction().toUpperCase();
        String target = request.getTarget();

        // 1. Sinh mã OTP 6 số bảo mật
        int otpCode = 100000 + secureRandom.nextInt(900000);
        String otpStr = String.valueOf(otpCode);

        // 2. Lưu vào Redis kèm TTL 120s
        String redisKey = "otp:" + action + ":" + target;
        String attemptsKey = "otp:attempts:" + action + ":" + target;

        redisTemplate.opsForValue().set(redisKey, otpStr, Duration.ofSeconds(OTP_TTL_SECONDS));
        redisTemplate.opsForValue().set(attemptsKey, "0", Duration.ofSeconds(OTP_TTL_SECONDS));

        log.info("🔐 [FINTECH OTP GENERATED] Mã OTP cho [{}] tới [{}] là: {} (Hết hạn sau 120s)", action, target, otpStr);

        return OtpResponse.builder()
                .message("Mã xác thực OTP đã được gửi thành công (Hiệu lực trong 2 phút)")
                .expiresInSeconds(OTP_TTL_SECONDS)
                .debugOtp(otpStr)
                .build();
    }

    public void verifyOtp(String action, String target, String inputOtp) {
        if (inputOtp == null || inputOtp.isBlank()) {
            throw new AppException(ErrorCode.OTP_INVALID, "Vui lòng nhập mã xác thực OTP");
        }

        String actionUpper = action.toUpperCase();
        String redisKey = "otp:" + actionUpper + ":" + target;
        String attemptsKey = "otp:attempts:" + actionUpper + ":" + target;

        String storedOtp = redisTemplate.opsForValue().get(redisKey);
        if (storedOtp == null) {
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;
        if (attempts >= MAX_OTP_ATTEMPTS) {
            redisTemplate.delete(redisKey);
            redisTemplate.delete(attemptsKey);
            throw new AppException(ErrorCode.OTP_MAX_ATTEMPTS);
        }

        if (!storedOtp.equals(inputOtp.trim())) {
            redisTemplate.opsForValue().increment(attemptsKey);
            int remaining = MAX_OTP_ATTEMPTS - (attempts + 1);
            throw new AppException(ErrorCode.OTP_INVALID, "Mã OTP không chính xác. Bạn còn " + Math.max(0, remaining) + " lần thử");
        }

        // OTP đúng -> Xóa khóa để không thể tái sử dụng (chống Replay Attack)
        redisTemplate.delete(redisKey);
        redisTemplate.delete(attemptsKey);
        log.info("✅ [FINTECH OTP VERIFIED] Xác thực OTP thành công cho [{}] - [{}]", actionUpper, target);
    }
}
