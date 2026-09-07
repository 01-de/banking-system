package com.banking.paymentservice.service;

import com.banking.paymentservice.dto.CreatePaymentRequest;
import com.banking.paymentservice.dto.PaymentOrderResponse;
import com.banking.paymentservice.dto.PaymentStatusResponse;
import com.banking.paymentservice.entity.Payment;
import com.banking.paymentservice.entity.PaymentStatus;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import repository.PaymentRepository;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@EnableJpaRepositories(basePackages = "repository")
public class PaymentService {
    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment.failed";
    private static final String CURRENCY = "usd";
    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    @Value("${stripe.secret-key}")
    private String secretKey;
    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    public PaymentOrderResponse createPaymentOrder(CreatePaymentRequest request) throws StripeException {
        log.info("Creating Payment Order for account: {} amount: {}", request.getAccountNumber(), request.getAmount());
        long convertedAmount = request.getAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue();

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(convertedAmount)
                .setCurrency(CURRENCY);
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            paramsBuilder.setDescription(request.getDescription());
        }
        PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());

        log.info("Stripe PaymentIntent created: {}", paymentIntent.getId());

        Payment payment = new Payment();
        payment.setStripePaymentIntentId(paymentIntent.getId());
        payment.setAccountNumber(request.getAccountNumber());
        payment.setAmount(request.getAmount());
        payment.setCurrency(CURRENCY);
        payment.setStatus(PaymentStatus.CREATED);
        payment.setDescription(request.getDescription());

        Payment savedPayment = paymentRepository.save(payment);

        return new PaymentOrderResponse(savedPayment.getId(), paymentIntent.getId(), request.getAmount(), CURRENCY, "CREATED", paymentIntent.getClientSecret());
    }

    public PaymentStatusResponse getPayment(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        return new PaymentStatusResponse(
                payment.getId(),
                payment.getStripePaymentIntentId(),
                payment.getAccountNumber(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getDescription(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    public void handleWebHook(String payload, String signature) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe webhook signature", e);
            return;
        }

        log.info("Received Webhook Event: {}", event.getType());
        if ("payment_intent.succeeded".equals(event.getType())) {
            handlePaymentSuccess(event);
        } else if ("payment_intent.payment_failed".equals(event.getType())) {
            handlePaymentFailure(event);
        }
    }


    private com.stripe.model.StripeObject deserializePaymentIntent(Event event) throws com.stripe.exception.EventDataObjectDeserializationException {
        com.stripe.model.EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent()) {
            return deserializer.getObject().get();
        }
        return deserializer.deserializeUnsafe();
    }

    private void handlePaymentSuccess(Event event) {
        try {
            PaymentIntent paymentIntent = (PaymentIntent) deserializePaymentIntent(event);
            Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntent.getId())
                    .orElseThrow(() -> new RuntimeException("Payment Intent: " + paymentIntent.getId() + " Not Found"));
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                log.info("Payment {} already COMPLETED - ignoring duplicate webhook delivery", payment.getId());
                return;
            }
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("paymentId", payment.getId());
            eventPayload.put("accountNumber", payment.getAccountNumber());
            eventPayload.put("amount", payment.getAmount());
            eventPayload.put("stripePaymentIntentId", paymentIntent.getId());
            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, payment.getId(), eventPayload);
            log.info("Payment Completed: {}", payment.getId());

        } catch (Exception e) {
            log.error("Error occurred while handling payment success", e);
        }
    }

    private void handlePaymentFailure(Event event) {
        try {
            PaymentIntent paymentIntent = (PaymentIntent) deserializePaymentIntent(event);
            Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntent.getId())
                    .orElseThrow(() -> new RuntimeException("Payment Intent: " + paymentIntent.getId() + " Not Found"));
            if (payment.getStatus() == PaymentStatus.FAILED) {
                log.info("Payment {} already FAILED - ignoring duplicate webhook delivery", payment.getId());
                return;
            }
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment failed via Stripe");
            paymentRepository.save(payment);

            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("paymentId", payment.getId());
            eventPayload.put("accountNumber", payment.getAccountNumber());
            eventPayload.put("amount", payment.getAmount());
            eventPayload.put("reason", "Payment Failed via Stripe");
            kafkaTemplate.send(PAYMENT_FAILED_TOPIC, payment.getId(), eventPayload);
            log.warn("Payment failed: {}", payment.getId());
        } catch (Exception e) {
            log.error("Error occurred while handling payment failure", e);
        }
    }
}
