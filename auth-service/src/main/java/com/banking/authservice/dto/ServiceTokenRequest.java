package com.banking.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTokenRequest {
    @NotBlank(message = "serviceId is required")
    private String serviceId;

    @NotBlank(message = "serviceSecret is required")
    private String serviceSecret;
}