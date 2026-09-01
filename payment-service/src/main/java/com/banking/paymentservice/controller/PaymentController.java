package com.banking.paymentservice.controller;

import com.banking.paymentservice.dto.CreatePaymentRequest;
import com.banking.paymentservice.dto.PaymentOrderResponse;
import com.banking.paymentservice.service.PaymentService;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<PaymentOrderResponse> createPaymentOrder(@Valid @RequestBody CreatePaymentRequest request) throws RazorpayException {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPaymentOrder(request));
    }

    // RazorPay webhook endpoint
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebHook(@RequestBody @Valid Map<String, Object> payload) {
        paymentService.handleWebHook(payload);
        return ResponseEntity.ok("Webhook processed successfully");
    }

    



}
