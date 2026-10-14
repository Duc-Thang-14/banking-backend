package com.bank.repository;

import com.bank.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {
    List<Beneficiary> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Beneficiary> findByUserIdAndBeneficiaryWalletNumber(Long userId, String beneficiaryWalletNumber);
}
