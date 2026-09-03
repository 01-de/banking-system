package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.event.TransactionCompletedEvent;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
@EnableFeignClients(basePackages = "com.banking.transactionservice.client")
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;



    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String TRANSACTION_FRAUD_DETECTED_TOPIC = "fraud.detected";

    public TransactionResponse transfer(@RequestBody TransferRequest request) {
        log.info("SAGA START - Transfer: {} -> {} amount: {}", request.getSenderAccountNumber(), request.getReceiverAccountNumber(), request.getAmount());
        accountServiceClient.deductBalance(request.getSenderAccountNumber(), request.getAmount());
        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());
        Transaction saved = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING: {}", saved.getId());

        TransactionInitiatedEvent event = new TransactionInitiatedEvent();
        event.setTransactionId(saved.getId());
        event.setSenderAccountNumber(saved.getSenderAccountNumber());
        event.setReceiverAccountNumber(saved.getReceiverAccountNumber());
        event.setAmount(saved.getAmount());
        event.setDescription(saved.getDescription());

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, event);
        log.info("SAGA STEP 2 - TransactionInitiatedEvent published: {}", saved.getId());
        return mapToResponse(saved);
    }

    public TransactionResponse getTransactionById(@PathVariable String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        return mapToResponse(transaction);
    }

    public List<TransactionResponse> getTransactionHistory(@PathVariable String accountNumber) {
        List<Transaction> transactions = transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber);
        return transactions.stream().map(transaction -> mapToResponse(transaction)).collect(Collectors.toList());
    }

    public TransactionResponse verifyOTP(String transactionID, String otp) {
        log.info("OTP verification for the transaction: {} otp: {}", transactionID, otp);
        Transaction transaction = transactionRepository.findById(transactionID).orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        String otpKey = "verification:otp:" + transactionID;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);
        if (storedOtp == null) {
            log.warn("OTP expired for the transaction: {}", transactionID);
            String reason = "OTP expired for the transaction: " + transactionID;
            compensateTransaction(transaction, reason);
            return mapToResponse(transaction);
        }
        if (!otp.equals(storedOtp)) {
            log.warn("OTP mismatch for the transaction: {}", transactionID);
            redisTemplate.delete(otpKey);
            String reason = "OTP mismatch for the transaction: " + transactionID;
            blockAccountAndCompensate(transaction, reason);
            return mapToResponse(transaction);
        }

        //OTP correct - complete transaction
        log.info("OTP verified - competing transaction: {}", transactionID);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);
        return mapToResponse(transaction);
    }

    public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if (transaction.getStatus() == TransactionStatus.PROCESSING) {
            log.warn("Transaction {} not PROCESSING - skipping", transactionId);
            return;
        }
        completeTransaction(transaction);
    }

    private void compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA COMPENSATION refunding: {} amount: {}", transaction.getSenderAccountNumber(), transaction.getAmount());
        // Credit money back to sender synchronously
        accountServiceClient.creditBalance(transaction.getSenderAccountNumber(), transaction.getAmount());
        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setDescription(reason + " - SAGA COMPENSATION executed, amount refunded at " + LocalDateTime.now());
        transactionRepository.save(transaction);

        // Publish refund event to Notification service with alert
        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, refundEvent);
        log.info("SAGA COMPENSATION COMPLETE: {} refunded to {}", transaction.getAmount(), transaction.getSenderAccountNumber());
    }

    private void blockAccountAndCompensate(Transaction transaction, String reason) {
        log.warn("SAGA BLOCKING transaction: {} amount: {}", transaction.getSenderAccountNumber(), transaction.getAmount());
        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("amount", transaction.getAmount());
        fraudEvent.put("reason", reason);
        kafkaTemplate.send(TRANSACTION_FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(), fraudEvent);
        log.warn("fraud.detected published - account: {} will be blocked", transaction.getSenderAccountNumber());

        // SAGA COMPENSATION refund amount ro sender

        compensateTransaction(transaction, reason);


    }

    private void completeTransaction(Transaction transaction) {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(transaction.getId(), transaction.getSenderAccountNumber(), transaction.getReceiverAccountNumber(), transaction.getAmount(), transaction.getDescription());
        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, completedEvent);

        log.info("SAGA COMPLETED transaction: {}", transaction.getId());

    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setDescription(transaction.getDescription());
        response.setAmount(transaction.getAmount());
        response.setStatus(transaction.getStatus());
        response.setReferenceNumber(transaction.getReferenceNumber());
        response.setSenderAccountNumber(transaction.getSenderAccountNumber());
        response.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        response.setFailureReason(transaction.getFailureReason());
        response.setType(transaction.getType());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setCompletedAt(transaction.getCompletedAt());
        return response;
    }
}
