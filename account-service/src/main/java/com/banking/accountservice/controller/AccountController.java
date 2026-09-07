package com.banking.accountservice.controller;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@Slf4j
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;

    @SuppressWarnings("Xss")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request, authentication.getName()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts(Authentication authentication) {
        return ResponseEntity.ok(accountService.getMyAccounts(authentication));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String accountNumber, Authentication authentication) {
        return ResponseEntity.ok(accountService.getAccount(accountNumber, authentication));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable String accountNumber, Authentication authentication) {
        return ResponseEntity.ok(accountService.getBalance(accountNumber, authentication));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{accountNumber}/block")
    public ResponseEntity<String> blockAccount(@PathVariable String accountNumber) {
        accountService.blockAccount(accountNumber);
        return ResponseEntity.ok("Account blocked Successfully");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{accountNumber}/unblock")
    public ResponseEntity<String> unblockAccount(@PathVariable String accountNumber) {
        accountService.unblockAccount(accountNumber);
        return ResponseEntity.ok("Account unblocked Successfully");
    }

    // Saga step 1 deduct balance - service-to-service only
    @PreAuthorize("hasRole('SERVICE')")
    @PutMapping("/{accountNumber}/deduct")
    public ResponseEntity<String> deductBalance(@PathVariable String accountNumber, @RequestParam BigDecimal amount) {
        accountService.deductBalance(accountNumber, amount);
        return ResponseEntity.ok("Balance deducted Successfully");
    }

    // step 4 compensating transaction - service-to-service only
    @PreAuthorize("hasRole('SERVICE')")
    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<String> creditBalance(@PathVariable String accountNumber, @RequestParam BigDecimal amount) {
        accountService.creditBalance(accountNumber, amount);
        return ResponseEntity.ok("Balance credited Successfully");
    }
}