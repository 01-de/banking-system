package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String OWNER_USER_ID = "user-1";

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private Account account;

    private Authentication ownerAuthentication(String userId) {
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        org.mockito.Mockito.lenient().when(authentication.getName()).thenReturn(userId);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        org.mockito.Mockito.lenient().doReturn(authorities).when(authentication).getAuthorities();
        return authentication;
    }

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId("acc-1");
        account.setUserId(OWNER_USER_ID);
        account.setAccountHolderName("John Doe");
        account.setAccountNumber("123456789012");
        account.setEmail("john@example.com");
        account.setPhone("+1234567890");
        account.setAccountType(AccountType.SAVINGS);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(new BigDecimal("500.00"));
        account.setDailyTransactionLimit(new BigDecimal("100000"));
    }

    @Test
    void createAccount_savesAccountWithGeneratedNumberAndActiveStatus() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountHolderName("Jane Doe");
        request.setEmail("jane@example.com");
        request.setPhone("+1987654321");
        request.setAccountType(AccountType.SAVINGS);
        request.setInitialDeposit(new BigDecimal("1000.00"));

        when(accountRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.createAccount(request, OWNER_USER_ID);

        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(response.getDailyTransactionLimit()).isEqualByComparingTo("100000");
        assertThat(response.getAccountNumber()).hasSize(12);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void createAccount_setsHigherDailyLimitForNonSavingsAccount() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountHolderName("Jane Doe");
        request.setEmail("jane@example.com");
        request.setPhone("+1987654321");
        request.setAccountType(AccountType.CURRENT);
        request.setInitialDeposit(new BigDecimal("1000.00"));

        when(accountRepository.existsByEmail(anyString())).thenReturn(false);
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.createAccount(request, OWNER_USER_ID);

        assertThat(response.getDailyTransactionLimit()).isEqualByComparingTo("300000");
    }

    @Test
    void createAccount_throwsWhenEmailAlreadyExists() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setEmail("john@example.com");

        when(accountRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(request, OWNER_USER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Account already exists");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void getAccount_returnsMappedResponseWhenFound() {
        when(accountRepository.findByAccountNumber("123456789012")).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getAccount("123456789012", ownerAuthentication(OWNER_USER_ID));

        assertThat(response.getAccountNumber()).isEqualTo("123456789012");
        assertThat(response.getAccountHolderName()).isEqualTo("John Doe");
    }

    @Test
    void getAccount_throwsWhenNotFound() {
        when(accountRepository.findByAccountNumber("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount("unknown", ownerAuthentication(OWNER_USER_ID)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void getBalance_returnsBalanceWhenFound() {
        when(accountRepository.findByAccountNumber("123456789012")).thenReturn(Optional.of(account));

        BigDecimal balance = accountService.getBalance("123456789012", ownerAuthentication(OWNER_USER_ID));

        assertThat(balance).isEqualByComparingTo("500.00");
    }

    @Test
    void blockAccount_setsStatusToBlockedAndSaves() {
        when(accountRepository.findByAccountNumber("123456789012")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.blockAccount("123456789012");

        assertThat(account.getStatus()).isEqualTo(AccountStatus.BLOCKED);
        verify(accountRepository).save(account);
    }

    @Test
    void deductBalance_subtractsAmountWhenSufficientFundsAndActive() {
        when(accountRepository.findByAccountNumber("123456789012")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.deductBalance("123456789012", new BigDecimal("200.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("300.00");
        verify(accountRepository).save(account);
    }

    @Test
    void deductBalance_throwsWhenInsufficientFunds() {
        when(accountRepository.findByAccountNumber("123456789012")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.deductBalance("123456789012", new BigDecimal("1000.00")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient funds");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void deductBalance_throwsWhenAccountNotActive() {
        account.setStatus(AccountStatus.BLOCKED);
        when(accountRepository.findByAccountNumber("123456789012")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.deductBalance("123456789012", new BigDecimal("100.00")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not active");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void creditBalance_addsAmountToBalance() {
        when(accountRepository.findByAccountNumber("123456789012")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.creditBalance("123456789012", new BigDecimal("250.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("750.00");
        verify(accountRepository, times(1)).save(account);
    }

    @Test
    void creditBalance_throwsWhenAccountNotFound() {
        when(accountRepository.findByAccountNumber("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.creditBalance("unknown", new BigDecimal("100.00")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Account not found");
    }
}
