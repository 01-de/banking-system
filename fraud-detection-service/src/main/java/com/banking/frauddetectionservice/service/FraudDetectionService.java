package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.model.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {
    private static final String VERIFICATION_REQUIRED_TOPIC = "verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC = "fraud.check.clean";
    private static final String AVG_KEY_PREFIX = "fraud:avg_amount:";
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    @Value("${fraud.max-transactions-per-minute}")
    private int maxTransactionPerMinute;
    @Value("${fraud.suspicious-amount-multiplier}")
    private double suspiciousAmountMultiplier;

    @Value("${fraud.max-balance-percentage}")
    private double maxBalancePercentage;

    public void checkTransaction(Map<String, Object> payload) {
        String transactionId = (String) payload.get("transactionId");
        String accountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = new BigDecimal((String) payload.get("amount"));

        //Fetch real balance from Account Service
        BigDecimal senderBalance = accountServiceClient.getAccountBalance(accountNumber);
        log.info("Checking transaction: {} transactionId {} accountNumber {} amount {} senderBalance", transactionId, accountNumber, amount, senderBalance);

        FraudCheckResult result = performFraudChecks(accountNumber, amount, senderBalance);
        if (result.isFraud()) {
            log.info("Suspicious activity detected - account {} reason {} - requesting OTP verification", accountNumber, result.getReason());
            Map<String, Object> verificationRequestEvent = new HashMap<>();
            verificationRequestEvent.put("transactionId", transactionId);
            verificationRequestEvent.put("senderAccountNumber", accountNumber);
            verificationRequestEvent.put("amount", amount);
            verificationRequestEvent.put("reason", result.getReason());
            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC, transactionId, verificationRequestEvent);
        } else {
            log.info("Transaction clean");
            Map<String, Object> transactionCleanEvent = new HashMap<>();
            transactionCleanEvent.put("transactionId", transactionId);
            transactionCleanEvent.put("isFraud", false);
            transactionCleanEvent.put("reason", null);
            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT_TOPIC, transactionId, transactionCleanEvent);
        }

    }

    private FraudCheckResult performFraudChecks(String accountNumber, BigDecimal amount, BigDecimal senderBalance) {
        // Velocity Check
        if (isVelocityExceeded(accountNumber)) {
            return new FraudCheckResult(true, "Too many transactions in 60 seconds " + " - Velocity limit reached");
        }

        //Amount check
        if (isAmountSuspicious(accountNumber, amount)) {
            return new FraudCheckResult(true, "Unusual transaction amount " + amount + " - exceeds 3x your average");
        }

        // Balance Check
        if (senderBalance.compareTo(BigDecimal.ZERO) > 0 && isBalanceCheckFailed(senderBalance, amount)) {
            return new FraudCheckResult(true, "Transaction exceed 90% of account balance");
        }

        return new FraudCheckResult(false, null);
    }

    private boolean isVelocityExceeded(String accountNumber) {
        String key = "fraud:velocity" + accountNumber;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }

        log.info("Velocity check - account: {} count {}/{}", accountNumber, count, maxTransactionPerMinute);
        return count != null && count > maxTransactionPerMinute;
    }

    private boolean isAmountSuspicious(String accountNumber, BigDecimal amount) {
        String yearMonth = YearMonth.now().toString();
        String key = AVG_KEY_PREFIX + accountNumber + ":" + yearMonth;

        Object sumObj = redisTemplate.opsForHash().get(key, "sumCents");
        Object countObj = redisTemplate.opsForHash().get(key, "count");

        boolean isSuspicious = false;

        if (sumObj != null && countObj != null) {
            long sumCents = Long.parseLong(sumObj.toString());
            long count = Long.parseLong(countObj.toString());

            BigDecimal avgAmount = BigDecimal.valueOf(sumCents, 2)
                    .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            BigDecimal threshold = avgAmount.multiply(BigDecimal.valueOf(suspiciousAmountMultiplier));

            isSuspicious = amount.compareTo(threshold) > 0;
            log.info("Amount check - amount: {} monthlyAvg: {} threshold: {} suspicious: {}",
                    amount, avgAmount, threshold, isSuspicious);
        }

        long amountCents = amount.movePointRight(2).longValueExact();
        redisTemplate.opsForHash().increment(key, "sumCents", amountCents);
        redisTemplate.opsForHash().increment(key, "count", 1);
        redisTemplate.expire(key, 45, TimeUnit.DAYS);

        return isSuspicious;
    }

    private boolean isBalanceCheckFailed(BigDecimal senderBalance, BigDecimal amount) {
        BigDecimal maxAllowed = senderBalance.multiply(BigDecimal.valueOf(maxBalancePercentage));
        log.info("Balance check - amount: {} senderBalance: {} maxAllowed: {} suspicious: {}", amount, senderBalance, maxAllowed, amount.compareTo(maxAllowed) > 0);
        return amount.compareTo(maxAllowed) > 0;
    }
}
