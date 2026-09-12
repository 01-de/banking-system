package com.banking.authservice.service;

import com.banking.authservice.dto.ServiceTokenRequest;
import com.banking.authservice.dto.ServiceTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ServiceAuthService {

    private final JwtService jwtService;

    @Value("${internal.service-credentials.transaction-service.secret}")
    private String transactionServiceSecret;

    @Value("${internal.service-credentials.fraud-detection-service.secret}")
    private String fraudDetectionServiceSecret;

    @Value("${internal.service-credentials.payment-service.secret}")
    private String paymentServiceSecret;

    public ServiceAuthService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public ServiceTokenResponse issueServiceToken(ServiceTokenRequest request) {
        String expectedSecret = switch (request.getServiceId()) {
            case "transaction-service" -> transactionServiceSecret;
            case "fraud-detection-service" -> fraudDetectionServiceSecret;
            case "payment-service" -> paymentServiceSecret;
            default -> null;
        };

        if (expectedSecret == null || !expectedSecret.equals(request.getServiceSecret())) {
            log.warn("Rejected service-token request for unknown/invalid serviceId: {}", request.getServiceId());
            throw new IllegalArgumentException("Invalid service credentials");
        }

        String token = jwtService.issueServiceToken(request.getServiceId());
        return new ServiceTokenResponse(token, jwtService.serviceTokenTtlSeconds());
    }
}