package com.swiftpay.gateway.controller;

import com.swiftpay.gateway.service.GatewayService;
import com.swiftpay.shared.dto.PaymentRequestDTO;
import com.swiftpay.shared.dto.PaymentResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/payments", "/v1/payments"})
public class PaymentGatewayController {

    private final GatewayService gatewayService;

    public PaymentGatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Initiate payment",
            description = "Accepts a payment initiation request. Validates mandatory transactionId, senderAccountId, receiverAccountId, amount > 0, and currency."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Accepted for asynchronous processing",
                    content = @Content(schema = @Schema(implementation = PaymentResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - validation failed"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    public PaymentResponseDTO initiatePayment(@Valid @RequestBody PaymentRequestDTO request) {
        return gatewayService.initiatePayment(request);
    }
}
