package com.banking.transactionservice.repository;

import com.banking.transactionservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findBySenderAccountNumberOrderByCreatedAtDesc(String senderAccountNumber);
    List<Transaction> findBySenderAccountNumberOrReceiverAccountNumberOrderByCreatedAtDesc(String senderAccountNumber, String receiverAccountNumber);
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}
