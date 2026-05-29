package com.swiftpay.shared.event;

import java.math.BigDecimal;
import java.time.Instant;

public class PaymentInitiatedEvent {

    private String transactionId;
    private String senderAccountId;
    private String receiverAccountId;
    private BigDecimal amount;
    private String currency;
    private Instant initiatedAt;

    public PaymentInitiatedEvent() {
    }

    public PaymentInitiatedEvent(
            String transactionId,
            String senderAccountId,
            String receiverAccountId,
            BigDecimal amount,
            String currency,
            Instant initiatedAt
    ) {
        this.transactionId = transactionId;
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.amount = amount;
        this.currency = currency;
        this.initiatedAt = initiatedAt;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getSenderAccountId() {
        return senderAccountId;
    }

    public String getReceiverAccountId() {
        return receiverAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getInitiatedAt() {
        return initiatedAt;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public void setSenderAccountId(String senderAccountId) {
        this.senderAccountId = senderAccountId;
    }

    public void setReceiverAccountId(String receiverAccountId) {
        this.receiverAccountId = receiverAccountId;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setInitiatedAt(Instant initiatedAt) {
        this.initiatedAt = initiatedAt;
    }
}
