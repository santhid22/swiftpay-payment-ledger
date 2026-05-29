package com.swiftpay.gateway.service;

import com.swiftpay.gateway.model.OutboundTransaction;
import com.swiftpay.gateway.repository.TransactionRepository;
import com.swiftpay.shared.dto.PaymentRequestDTO;
import com.swiftpay.shared.dto.PaymentResponseDTO;
import com.swiftpay.shared.event.PaymentInitiatedEvent;
import com.swiftpay.shared.event.PaymentResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class GatewayService {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);


    private final StringRedisTemplate redisTemplate;
    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, PaymentInitiatedEvent> kafkaTemplate;
    private final String initiatedTopic;

    public GatewayService(
            StringRedisTemplate redisTemplate,
            TransactionRepository transactionRepository,
            KafkaTemplate<String, PaymentInitiatedEvent> kafkaTemplate,
            @Value("${swiftpay.kafka.topics.payment-initiated}") String initiatedTopic
    ) {
        this.redisTemplate = redisTemplate;
        this.transactionRepository = transactionRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.initiatedTopic = initiatedTopic;
    }

    @Transactional
    public PaymentResponseDTO initiatePayment(PaymentRequestDTO request) {
        log.info("Received payment request. txId={}, sender={}, receiver={}, amount={}, currency={}",
                request.getTransactionId(), request.getSenderAccountId(), request.getReceiverAccountId(),
                request.getAmount(), request.getCurrency());
        String redisKey = "idempotency:" + request.getTransactionId();
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(redisKey, "LOCKED", Duration.ofHours(24));

        if (Boolean.FALSE.equals(lockAcquired)) {
            log.warn("Idempotency lock hit for txId={} with redisKey={}", request.getTransactionId(), redisKey);
            OutboundTransaction existing = transactionRepository.findByTransactionId(request.getTransactionId())
                    .orElseThrow(() -> new IllegalStateException("Duplicate transaction lock exists without persisted record"));
            return new PaymentResponseDTO(
                    existing.getTransactionId(),
                    existing.getStatus(),
                    "Duplicate request detected; returning latest known status",
                    Instant.now()
            );
        }

        OutboundTransaction tx = new OutboundTransaction();
        tx.setTransactionId(request.getTransactionId());
        tx.setSenderAccountId(request.getSenderAccountId());
        tx.setReceiverAccountId(request.getReceiverAccountId());
        tx.setAmount(request.getAmount());
        tx.setCurrency(request.getCurrency());
        tx.setStatus("PENDING");
        transactionRepository.save(tx);
        log.info("Persisted outbound transaction as PENDING. txId={}", tx.getTransactionId());

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                request.getTransactionId(),
                request.getSenderAccountId(),
                request.getReceiverAccountId(),
                request.getAmount(),
                request.getCurrency(),
                Instant.now()
        );

        kafkaTemplate.send(initiatedTopic, request.getTransactionId(), event);
        log.info("Emitted payment initiated event to Kafka. topic={}, txId={}", initiatedTopic, request.getTransactionId());
        log.info("Gateway transaction committed. txId={}", request.getTransactionId());

        return new PaymentResponseDTO(
                request.getTransactionId(),
                "PENDING",
                "Transaction accepted and queued for ledger processing",
                Instant.now()
        );
    }

    @Transactional
    @KafkaListener(
            topics = "${swiftpay.kafka.topics.payment-result}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPaymentResult(PaymentResultEvent event) {
        log.info("Received payment result event. txId={}, status={}, reason={}",
                event.getTransactionId(), event.getStatus(), event.getReason());
        Optional<OutboundTransaction> optTx = transactionRepository.findByTransactionId(event.getTransactionId());
        if (optTx.isEmpty()) {
            log.warn("No outbound transaction found for incoming result. txId={}", event.getTransactionId());
            return;
        }

        OutboundTransaction tx = optTx.get();
        tx.setStatus(event.getStatus());
        tx.setFailureReason(event.getReason());
        transactionRepository.save(tx);
        log.info("Updated outbound transaction status from result event. txId={}, status={}",
                tx.getTransactionId(), tx.getStatus());
    }
}
