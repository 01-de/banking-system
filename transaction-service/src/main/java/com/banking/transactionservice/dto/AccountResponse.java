package com.banking.transactionservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AccountResponse {
    private String accountNumber;
    private String userId;
    private String email;
}
