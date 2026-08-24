package com.banking.accountservice.dto.dto;

import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountResponse {
    private String id;

    private String accountHolderName;

    private String accountNumber;

    private String email;

    private String phone;

    private AccountType accountType;


    private AccountStatus status;


    private BigDecimal balance;


    private BigDecimal dailyTransactionLimit;

}
