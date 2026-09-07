package com.banking.accountservice.repository;

import com.banking.accountservice.entity.CreditedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditedTransactionRepository extends JpaRepository<CreditedTransaction, String> {
}