package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

//Consume verification.required
//Generate OTP and ask user to verify


@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer {
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final AccountServiceClient accountServiceClient;
    private static final long OTP_EXPIRY_MINUTES = 5;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TRANSACTION_OTP_TOPIC = "transaction.otp.generated";

    @KafkaListener(topics = "verification.required")
    public void consumeVerificationRequired(@Payload Map<String, Object> payload) {
        try {
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("senderAccountNumber");
            String reason = (String) payload.get("reason");
            log.info("Verification required - transaction: {} reason: {} ", transactionId, reason);

            Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

            if (transaction.getStatus() != TransactionStatus.PROCESSING) {
                log.warn("Transaction status not PROCESSING - transaction: {} reason: {} ", transactionId, reason);
                return;
            }

            //Claim the transition first so a concurrent redelivery loses the optimistic-lock race
            //before it can overwrite the OTP or re-publish the notification
            transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
            try {
                transactionRepository.save(transaction);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Transaction {} already being verified by a concurrent delivery - skipping", transactionId);
                return;
            }

            String otp = String.format("%06d", (int) (Math.random() * 900000) + 100000);
            log.info("Generated OTP for transaction: {} - OTP: {}", transactionId, otp);

            //Store OTP in redis which will be expired in 5 minutes
            String otpKey = "verification:otp:" + transactionId;
            redisTemplate.opsForValue().set(otpKey, otp, OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);

            log.info("OTP generated for transaction: {} expires in {} min",  transactionId, OTP_EXPIRY_MINUTES);

            Map<String, Object> otpEvent = new HashMap<>();
            otpEvent.put("transactionId", transactionId);
            otpEvent.put("accountNumber", accountNumber);
            otpEvent.put("reason", reason);
            otpEvent.put("otp", otp);
            otpEvent.put("amount", payload.get("amount"));
            otpEvent.put("email", accountServiceClient.getAccount(accountNumber).getEmail());
            kafkaTemplate.send(TRANSACTION_OTP_TOPIC, transactionId, otpEvent);


        } catch (Exception e) {
            log.error("Error occurred while consuming verification required event", e);
        }
    }

    @KafkaListener(topics = "fraud.check.clean")
    public void consumeFraudCheckCleanResult(@Payload Map<String, Object> payload) {
        try {
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");
            transactionService.processCleanResult(transactionId);

        } catch (Exception e) {
            log.error("Error processing fraud check result", e);
        }
    }

    @KafkaListener(topics = "fraud.check.failed")
    public void consumeFraudCheckFailed(@Payload Map<String, Object> payload) {
        try {
            String transactionId = (String) payload.get("transactionId");
            String reason = (String) payload.get("reason");
            log.warn("Fraud check failed - transaction: {} reason: {} - compensating", transactionId, reason);
            transactionService.processFraudCheckFailure(transactionId, reason);
        } catch (Exception e) {
            log.error("Error processing fraud check failure", e);
        }
    }

    @KafkaListener(topics = "payment.completed")
    public void consumePaymentCompleted(@Payload Map<String, Object> payload) {
        try {
            String paymentId = (String) payload.get("paymentId");
            String userId = (String) payload.get("userId");
            String accountNumber = (String) payload.get("accountNumber");
            String stripePaymentIntentId = (String) payload.get("stripePaymentIntentId");
            java.math.BigDecimal amount = new java.math.BigDecimal(payload.get("amount").toString());
            transactionService.processDeposit(paymentId, accountNumber, userId, amount, stripePaymentIntentId);
        } catch (Exception e) {
            log.error("Error processing payment completed event", e);
        }
    }
}
