package com.bank.service;

import com.bank.dto.request.BeneficiaryRequest;
import com.bank.dto.response.BeneficiaryResponse;
import com.bank.entity.Beneficiary;
import com.bank.entity.User;
import com.bank.exception.AppException;
import com.bank.exception.ErrorCode;
import com.bank.repository.BeneficiaryRepository;
import com.bank.repository.UserRepository;
import com.bank.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> getBeneficiaries() {
        User user = getCurrentUser();
        return beneficiaryRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(BeneficiaryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public BeneficiaryResponse addBeneficiary(BeneficiaryRequest request) {
        User user = getCurrentUser();

        // Kiểm tra xem số ví thụ hưởng có tồn tại trên hệ thống không
        if (!walletRepository.existsByWalletNumber(request.getBeneficiaryWalletNumber())) {
            throw new AppException(ErrorCode.WALLET_NOT_FOUND, "Số ví người nhận không tồn tại trên hệ thống");
        }

        // Kiểm tra xem đã lưu trong danh bạ chưa
        return beneficiaryRepository.findByUserIdAndBeneficiaryWalletNumber(user.getId(), request.getBeneficiaryWalletNumber())
                .map(existing -> {
                    existing.setNickname(request.getNickname());
                    return BeneficiaryResponse.fromEntity(beneficiaryRepository.save(existing));
                })
                .orElseGet(() -> {
                    Beneficiary beneficiary = Beneficiary.builder()
                            .user(user)
                            .beneficiaryWalletNumber(request.getBeneficiaryWalletNumber())
                            .nickname(request.getNickname())
                            .build();
                    return BeneficiaryResponse.fromEntity(beneficiaryRepository.save(beneficiary));
                });
    }

    @Transactional
    public void deleteBeneficiary(Long id) {
        User user = getCurrentUser();
        Beneficiary beneficiary = beneficiaryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REQUEST, "Không tìm thấy người thụ hưởng"));

        if (!beneficiary.getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        beneficiaryRepository.delete(beneficiary);
    }
}
