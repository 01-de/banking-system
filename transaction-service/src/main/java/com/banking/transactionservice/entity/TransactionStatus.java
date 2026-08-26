package com.banking.transactionservice.entity;


// Transaction Lifecycle Flow:
// PENDING PROCESSING COMPLETED clean transactions
//                    PENDING_VERIFICATION suspicious detected
//                                 COMPLETED verified
//                                 FLAGGED saga refund
//                    FAILED
//                    FLAGGED
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    COMPLETED,
    FAILED,
    FLAGGED
}
