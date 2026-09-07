package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AuthServiceClient;
import com.banking.frauddetectionservice.dto.ServiceTokenRequest;
import com.banking.frauddetectionservice.dto.ServiceTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ServiceTokenProvider {

    private static final String CACHE_KEY = "service-token:fraud-detection-service";
    private static final long EXPIRY_SAFETY_MARGIN_SECONDS = 10;

    private final AuthServiceClient authServiceClient;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${internal.service-id}")
    private String serviceId;

    @Value("${internal.service-secret}")
    private String serviceSecret;

    public String getToken() {
        String cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            return cached;
        }

        ServiceTokenResponse response = authServiceClient.serviceToken(new ServiceTokenRequest(serviceId, serviceSecret));
        long ttl = Math.max(response.getExpiresIn() - EXPIRY_SAFETY_MARGIN_SECONDS, 1);
        redisTemplate.opsForValue().set(CACHE_KEY, response.getAccessToken(), ttl, TimeUnit.SECONDS);
        return response.getAccessToken();
    }
}