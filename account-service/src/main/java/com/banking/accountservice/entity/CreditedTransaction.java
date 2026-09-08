package com.banking.accountservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "credited_transaction")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditedTransaction {
    @Id
    private String transactionId;

    @CreationTimestamp
    private LocalDateTime creditedAt;
}
