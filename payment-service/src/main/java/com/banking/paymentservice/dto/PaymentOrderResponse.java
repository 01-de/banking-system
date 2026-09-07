package com.banking.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentOrderResponse {
    private String paymentId;

    private String stripePaymentIntentId;
    private BigDecimal amount;
    private String currency;

    private String status;

    private String clientSecret;

}
