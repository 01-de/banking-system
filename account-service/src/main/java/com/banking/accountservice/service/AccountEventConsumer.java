package com.banking.accountservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {

    private final AccountService accountService;

    // Consume transaction.completed from kafka
    // No fraud detected and crediting receiver

    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(@Payload Map<String, Object> payload) {
        try {
            String receiverAccountNumber = (String) payload.get("receiverAccountNumber");
            BigDecimal amount = new BigDecimal((String) payload.get("amount"));
            log.info("Crediting account: {} amount: {}", receiverAccountNumber, amount);
            accountService.creditBalance(receiverAccountNumber, amount);
        } catch (Exception e) {
            log.error("Error while credit account: {}", e.getMessage());
        }
    }

    // Consume fraud.detected event from kafka
    // Blocks the flagged account
    @KafkaListener(topics = "transaction.completed")
    public void consumeFraudDetected(@Payload Map<String, Object> payload) {
        try  {
            String accountNumber = (String) payload.get("accountNumber");
            log.info("Fraud detected from account: {}", accountNumber);
            accountService.blockAccount(accountNumber);

        } catch (Exception e) {
            log.error("Error while handling fraud detected: {}", e.getMessage());
        }
    }
}
