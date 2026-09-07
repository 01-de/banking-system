package com.banking.transactionservice.client;

import com.banking.transactionservice.dto.ServiceTokenRequest;
import com.banking.transactionservice.dto.ServiceTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "auth-service", url = "${auth.service.url}")
public interface AuthServiceClient {
    @PostMapping("/api/v1/auth/service-token")
    ServiceTokenResponse serviceToken(@RequestBody ServiceTokenRequest request);
}