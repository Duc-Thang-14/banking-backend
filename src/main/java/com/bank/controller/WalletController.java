package com.bank.controller;

import com.bank.dto.request.TopUpRequest;
import com.bank.dto.request.WithdrawRequest;
import com.bank.dto.response.TransactionHistoryResponse;
import com.bank.dto.response.WalletResponse;
import com.bank.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bank.dto.response.PageResponse;
import com.bank.enums.TransactionType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/{walletNumber}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable String walletNumber) {
        return ResponseEntity.ok(walletService.getWalletByNumber(walletNumber));
    }

    @GetMapping("/{walletNumber}/transactions")
    public ResponseEntity<List<TransactionHistoryResponse>> getTransactionHistory(@PathVariable String walletNumber) {
        return ResponseEntity.ok(walletService.getTransactionHistory(walletNumber));
    }

    @GetMapping("/{walletNumber}/transactions/search")
    public ResponseEntity<PageResponse<TransactionHistoryResponse>> searchTransactions(
            @PathVariable String walletNumber,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(walletService.searchTransactions(walletNumber, type, pageable));
    }

    @PostMapping("/top-up")
    public ResponseEntity<WalletResponse> topUp(@Valid @RequestBody TopUpRequest request) {
        return ResponseEntity.ok(walletService.topUp(request));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<WalletResponse> withdraw(@Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.ok(walletService.withdraw(request));
    }
}
