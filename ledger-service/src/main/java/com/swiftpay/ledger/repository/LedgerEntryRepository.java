package com.swiftpay.ledger.repository;

import com.swiftpay.ledger.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(String accountId);
    List<LedgerEntry> findByTransactionId(String transactionId);
}
