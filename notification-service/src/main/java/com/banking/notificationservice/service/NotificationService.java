package com.banking.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class NotificationService {
    @KafkaListener(topics = "transaction.otp.generation")
    public void consumeOtpGeneration(@Payload Map<String, Object> payload) {
        try {
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            String otp = (String) payload.get("otp");
            String amount = payload.get("amount").toString();
            String reason = (String) payload.get("reason");

            sendAlert(accountNumber, "TRANSACTION VERIFICATION REQUIRED", String.format("Suspicious activity detected on your account. " + "Reason: %s " + "A transaction of %s is pending verification. " + "Your OTP is: %s. Valid for 5 minutes. " + "If this wasn't you - ignore this message.", reason, amount));
        } catch (Exception e) {
            log.error("Error occurred while consuming OTP generation message", e);
        }
    }

    private void sendAlert(String accountNumber, String subject, String message) {

    }
}
