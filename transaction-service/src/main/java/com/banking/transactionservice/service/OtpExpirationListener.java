package com.banking.transactionservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OtpExpirationListener extends KeyExpirationEventMessageListener {

    private static final String OTP_KEY_PREFIX = "verification:otp:";

    private final TransactionService transactionService;

    public OtpExpirationListener(RedisMessageListenerContainer listenerContainer, TransactionService transactionService) {
        super(listenerContainer);
        this.transactionService = transactionService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        if (!expiredKey.startsWith(OTP_KEY_PREFIX)) {
            return;
        }

        String transactionId = expiredKey.substring(OTP_KEY_PREFIX.length());
        try {
            log.info("OTP key expired with no action taken - transaction: {}", transactionId);
            transactionService.handleOtpExpired(transactionId);
        } catch (Exception e) {
            log.error("Error handling OTP expiry for transaction: {}", transactionId, e);
        }
    }
}