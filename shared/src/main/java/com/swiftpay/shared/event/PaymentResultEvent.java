package com.swiftpay.shared.event;

import java.time.Instant;

public class PaymentResultEvent {

    private String transactionId;
    private String status;
    private String reason;
    private Instant processedAt;

    public PaymentResultEvent() {
    }

    public PaymentResultEvent(String transactionId, String status, String reason, Instant processedAt) {
        this.transactionId = transactionId;
        this.status = status;
        this.reason = reason;
        this.processedAt = processedAt;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
