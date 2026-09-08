package com.banking.frauddetectionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTokenResponse {
    private String accessToken;
    private long expiresIn;
}