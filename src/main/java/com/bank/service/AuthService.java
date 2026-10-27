package com.bank.service;

import com.bank.dto.request.LoginRequest;
import com.bank.dto.request.RegisterRequest;
import com.bank.dto.response.AuthResponse;
import com.bank.entity.User;
import com.bank.entity.Wallet;
import com.bank.enums.UserStatus;
import com.bank.enums.WalletStatus;
import com.bank.enums.WalletType;
import com.bank.exception.AppException;
import com.bank.exception.ErrorCode;
import com.bank.repository.UserRepository;
import com.bank.repository.WalletRepository;
import com.bank.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS, "Username đã tồn tại");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS, "Email đã tồn tại");
        }

        // 1. Tạo User
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);

        // 2. Tự động tạo Ví chính (MAIN WALLET)
        String generatedWalletNumber = generateUniqueWalletNumber();
        Wallet mainWallet = Wallet.builder()
                .walletNumber(generatedWalletNumber)
                .user(user)
                .walletType(WalletType.MAIN)
                .balance(BigDecimal.ZERO)
                .currency("VND")
                .status(WalletStatus.ACTIVE)
                .build();
        walletRepository.save(mainWallet);

        // 3. Tạo Tokens
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .username(user.getUsername())
                .defaultWalletNumber(mainWallet.getWalletNumber())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String walletNumber = walletRepository.findByUserId(user.getId()).stream()
                .filter(w -> w.getWalletType() == WalletType.MAIN || w.getWalletType() == WalletType.SYSTEM)
                .findFirst()
                .map(Wallet::getWalletNumber)
                .orElse(null);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .username(user.getUsername())
                .defaultWalletNumber(walletNumber)
                .build();
    }

    private String generateUniqueWalletNumber() {
        Random random = new Random();
        String number;
        do {
            number = "W" + (1000000000L + (long) (random.nextDouble() * 8999999999L));
        } while (walletRepository.findByWalletNumber(number).isPresent());
        return number;
    }
}
