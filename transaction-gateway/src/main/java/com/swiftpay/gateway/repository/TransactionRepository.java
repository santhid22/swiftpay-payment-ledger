package com.swiftpay.gateway.repository;

import com.swiftpay.gateway.model.OutboundTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<OutboundTransaction, Long> {
    Optional<OutboundTransaction> findByTransactionId(String transactionId);
}
