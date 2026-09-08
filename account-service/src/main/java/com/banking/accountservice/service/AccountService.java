package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private static final SecureRandom secureRandom = new SecureRandom();

    public AccountResponse createAccount(CreateAccountRequest request, String userId) {
        log.info("Creating account request for:{}", request.getEmail());
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Account already exists for email: " + request.getEmail());
        }

        Account account = new Account();
        account.setUserId(userId);
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        account.setAccountNumber(generateAccountNumber());
        account.setDailyTransactionLimit(request.getAccountType() == AccountType.SAVINGS ? new BigDecimal("100000") : new BigDecimal("300000"));
        Account savedAccount = accountRepository.save(account);
        log.info("Saved account for:{}", savedAccount.getAccountNumber());
        return mapToResponse(savedAccount);

    }

    public List<AccountResponse> getMyAccounts(Authentication authentication) {
        return accountRepository.findByUserId(authentication.getName()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public AccountResponse getAccount(String accountNumber, Authentication authentication) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new RuntimeException("Account not found for accountNumber: " + accountNumber));
        requireOwnerOrAdmin(account, authentication);
        return mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber, Authentication authentication) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new RuntimeException("Account not found for accountNumber: " + accountNumber));
        requireOwnerOrAdmin(account, authentication);
        return account.getBalance();
    }

    private void requireOwnerOrAdmin(Account account, Authentication authentication) {
        boolean isAdminOrService = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_SERVICE"));
        if (isAdminOrService) {
            return;
        }
        if (!account.getUserId().equals(authentication.getName())) {
            throw new AccessDeniedException("You do not have access to account: " + account.getAccountNumber());
        }
    }

    // Block account called by Fraud detection via Kafka
    public void blockAccount(String accountNumber) {
        log.info("Blocking account for:{}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new RuntimeException("Account not found for accountNumber: " + accountNumber));
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Blocked account for:{}", accountNumber);
    }

    // Unblock account
    public void unblockAccount(String accountNumber) {
        log.info("Unblocking account for:{}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new RuntimeException("Account not found for accountNumber: " + accountNumber));
        if (account.getStatus() != AccountStatus.BLOCKED) {
            throw new RuntimeException("Account is not blocked for accountNumber: " + accountNumber);
        }
        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);
        log.info("Unblocked account for:{}", accountNumber);
    }

    // Deduct balance called by Transaction Service
    public void deductBalance(String accountNumber, BigDecimal amount) {
        log.info("Deducting balance from account:{}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new RuntimeException("Account not found for accountNumber: " + accountNumber));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Account is not active for accountNumber: " + accountNumber);
        }
        if (account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds for account: " + accountNumber);
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Balance deducted from account:{}", accountNumber);
    }

    // Credit balance called by Transaction Service
    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Crediting balance from account:{}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new RuntimeException("Account not found for accountNumber: " + accountNumber));
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Credit balance credited from account:{}", accountNumber);
    }

    private AccountResponse mapToResponse(Account savedAccount) {
        AccountResponse accountResponse = new AccountResponse();
        accountResponse.setId(savedAccount.getId());
        accountResponse.setUserId(savedAccount.getUserId());
        accountResponse.setAccountHolderName(savedAccount.getAccountHolderName());
        accountResponse.setAccountNumber(savedAccount.getAccountNumber());
        accountResponse.setEmail(savedAccount.getEmail());
        accountResponse.setPhone(savedAccount.getPhone());
        accountResponse.setAccountType(savedAccount.getAccountType());
        accountResponse.setStatus(savedAccount.getStatus());
        accountResponse.setBalance(savedAccount.getBalance());
        accountResponse.setDailyTransactionLimit(savedAccount.getDailyTransactionLimit());
        return accountResponse;
    }

    private String generateAccountNumber() {
        final long BOUND = 1_000_000_000_000L;
        final int MAX_ATTEMPTS = 10;

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String accountNumber = String.format("%012d", secureRandom.nextLong(BOUND));
            if (!accountRepository.existsByAccountNumber(accountNumber)) {
                return accountNumber;
            }
        }
        throw new IllegalStateException("Unable to generate unique account number after " + MAX_ATTEMPTS);
    }
}
