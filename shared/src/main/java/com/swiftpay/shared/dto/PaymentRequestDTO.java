package com.swiftpay.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class PaymentRequestDTO {

    @NotBlank
    @Schema(description = "Globally unique client transaction id", example = "tx-20260528-0001")
    private String transactionId;

    @NotBlank
    @Schema(description = "Sender account id", example = "acct-sender-001")
    private String senderAccountId;

    @NotBlank
    @Schema(description = "Receiver account id", example = "acct-receiver-009")
    private String receiverAccountId;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    @Schema(description = "Transfer amount, must be greater than 0", example = "150.75")
    private BigDecimal amount;

    @NotBlank
    @Schema(description = "ISO currency code for transaction", example = "USD")
    private String currency;

    public PaymentRequestDTO() {
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getSenderAccountId() {
        return senderAccountId;
    }

    public void setSenderAccountId(String senderAccountId) {
        this.senderAccountId = senderAccountId;
    }

    public String getReceiverAccountId() {
        return receiverAccountId;
    }

    public void setReceiverAccountId(String receiverAccountId) {
        this.receiverAccountId = receiverAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
