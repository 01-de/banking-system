package com.banking.paymentservice.dto;

import com.banking.paymentservice.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {
    private String paymentId;

    private String stripePaymentIntentId;

    private String accountNumber;

    private BigDecimal amount;

    private String currency;

    private PaymentStatus status;

    private String description;

    private String failureReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
