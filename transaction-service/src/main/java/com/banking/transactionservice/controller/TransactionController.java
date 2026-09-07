package com.banking.transactionservice.controller;

import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.banking.transactionservice.service.TransactionService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@Slf4j
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.transfer(request, authentication.getName()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable String transactionId, Authentication authentication) {
        return ResponseEntity.ok(transactionService.getTransactionById(transactionId, authentication));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory(@PathVariable String accountNumber, Authentication authentication) {
        return ResponseEntity.ok(transactionService.getTransactionHistory(accountNumber, authentication));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{transactionId}/verify")
    public ResponseEntity<TransactionResponse> verifyOTP(@RequestParam String otp, @PathVariable String transactionId, Authentication authentication) {
        log.info("OTP verification request - transaction: {}", transactionId);
        return ResponseEntity.ok(transactionService.verifyOTP(transactionId, otp, authentication));
    }




}