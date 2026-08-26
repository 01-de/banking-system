package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;



    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_FINISHED_TOPIC = "transaction.finished";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    
    public TransactionResponse transfer(@RequestBody TransferRequest request) {
        log.info("SAGA START - Transfer: {} -> {} amount: {}", request.getSenderAccountNumber(), request.getReceiverAccountNumber(), request.getAmount());
        accountServiceClient.deductBalance(request.getReceiverAccountNumber(), request.getAmount());
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
