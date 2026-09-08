package com.banking.frauddetectionservice.config;

import com.banking.frauddetectionservice.service.ServiceTokenProvider;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class FeignServiceAuthConfig {

    @Bean
    public RequestInterceptor serviceAuthRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return requestTemplate -> requestTemplate.header("Authorization", "Bearer " + serviceTokenProvider.getToken());
    }
}