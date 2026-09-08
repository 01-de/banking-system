package com.banking.paymentservice.controller;

import com.banking.paymentservice.dto.CreatePaymentRequest;
import com.banking.paymentservice.dto.PaymentOrderResponse;
import com.banking.paymentservice.dto.PaymentStatusResponse;
import com.banking.paymentservice.service.PaymentService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/create-order")
    public ResponseEntity<PaymentOrderResponse> createPaymentOrder(@Valid @RequestBody CreatePaymentRequest request, Authentication authentication) throws StripeException {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPaymentOrder(request, authentication.getName()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentStatusResponse> getPayment(@PathVariable String paymentId, Authentication authentication) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId, authentication));
    }

    // Stripe webhook endpoint - requires the raw request body for signature verification, no user JWT
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebHook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
        paymentService.handleWebHook(payload, signature);
        return ResponseEntity.ok("Webhook processed successfully");
    }



}