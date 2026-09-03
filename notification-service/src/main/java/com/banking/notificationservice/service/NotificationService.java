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

            sendAlert(accountNumber, "TRANSACTION VERIFICATION REQUIRED", String.format("Suspicious activity detected on your account. " + "Reason: %s " + "A transaction of %s is pending verification. " + "Your OTP is: %s. Valid for 5 minutes. " + "If this wasn't you - ignore this message.", reason, amount, otp));
        } catch (Exception e) {
            log.error("Error occurred while consuming OTP generation message", e);
        }
    }

    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompletion(@Payload Map<String, Object> payload) {
        try {
            String senderAccount =  (String) payload.get("senderAccountNumber");
            String receiverAccount =  (String) payload.get("receiverAccountNumber");
            String amount = (String) payload.get("amount");
            //Debit alert
            sendAlert(senderAccount, "DEBIT ALERT", String.format("%s debited from account %s", amount, senderAccount));

            // Credit alert
            sendAlert(receiverAccount, "CREDIT ALERT", String.format("%s credited to account %s", amount, receiverAccount));

        } catch (Exception e) {
            log.error("Error occurred while consuming transaction notification completion message", e);
        }
    }

    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(@Payload Map<String, Object> payload) {
        try {
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");
            sendAlert(accountNumber, "SUSPICIOUS ACTIVITY DETECTED", String.format("Your account %s has been blocked. " + "Reason: %s" + "Please contact your bank immediately.", accountNumber, reason));
        } catch (Exception e) {
            log.error("Error occurred while consuming fraud detection message", e);
        }
    }

    @KafkaListener(topics = "transaction.refunded")
    public void consumeTransactionRefunded(@Payload Map<String, Object> payload) {
        try {
            String senderAccount =  (String) payload.get("senderAccountNumber");
            String amount = (String) payload.get("amount");
            String reason = (String) payload.get("reason");
            sendAlert(senderAccount, "REFUND PROCESSED", String.format("A transaction of %s has been refunded to your account. " + "Reason: %s", amount, reason));
        } catch (Exception e) {
            log.error("Error occurred while consuming transaction refunded message", e);
        }
    }

    @KafkaListener(topics = "payment.completed")
    public void consumePaymentCompleted(@Payload Map<String, Object> payload) {
        try {
            String accountNumber = (String) payload.get("accountNumber");
            String amount = (String) payload.get("amount");
            String razorpayPaymentId = (String) payload.get("razorpayPaymentId");
            sendAlert(accountNumber, "PAYMENT COMPLETED", String.format("A payment of %s has been completed for your account." + "Razorpay ID: %s", amount, razorpayPaymentId));
        } catch (Exception e) {
            log.error("Error occurred while consuming payment notification message", e);
        }
    }

    public void consumePaymentFailed(@Payload Map<String, Object> payload) {
        try {
            String accountNumber = (String) payload.get("accountNumber");
            String amount = (String) payload.get("amount");
            String reason = (String) payload.get("reason");
            sendAlert(accountNumber, "PAYMENT FAILED", String.format("A payment of %s has failed for your account. " + "Reason: %s", amount, reason));
        } catch (Exception e) {
            log.error("Error occurred while consuming payment notification message", e);
        }
    }

    private void sendAlert(String accountNumber, String subject, String message) {
        log.info("--------------------------------");
        log.info("Account number: {}", accountNumber);
        log.info("Subject : {}", subject);
        log.info("Message : {}", message);
        log.info("---------------------------------");
    }
}
