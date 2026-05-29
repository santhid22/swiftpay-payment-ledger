package com.swiftpay.shared.dto;

import java.time.Instant;

public class PaymentResponseDTO {

    private String transactionId;
    private String status;
    private String message;
    private Instant processedAt;

    public PaymentResponseDTO() {
    }

    public PaymentResponseDTO(String transactionId, String status, String message, Instant processedAt) {
        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
        this.processedAt = processedAt;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
