package com.banking.transactionservice.config;

import com.banking.transactionservice.service.ServiceTokenProvider;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class FeignServiceAuthConfig {

    @Bean
    public RequestInterceptor serviceAuthRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return requestTemplate -> requestTemplate.header("Authorization", "Bearer " + serviceTokenProvider.getToken());
    }
}