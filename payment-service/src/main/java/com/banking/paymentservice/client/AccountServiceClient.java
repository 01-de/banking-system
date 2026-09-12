package com.banking.paymentservice.client;

import com.banking.paymentservice.config.FeignServiceAuthConfig;
import com.banking.paymentservice.dto.AccountResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "account-service", url = "${account.service.url}", configuration = FeignServiceAuthConfig.class)
public interface AccountServiceClient {
    @GetMapping("/api/v1/accounts/{accountNumber}")
    AccountResponse getAccount(@PathVariable String accountNumber);
}