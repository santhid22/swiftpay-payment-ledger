package com.swiftpay.ledger.service;

import com.swiftpay.ledger.model.Account;
import com.swiftpay.ledger.model.LedgerEntry;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.LedgerEntryRepository;
import com.swiftpay.shared.event.PaymentInitiatedEvent;
import com.swiftpay.shared.event.PaymentResultEvent;
import com.swiftpay.shared.exception.AccountNotFoundException;
import com.swiftpay.shared.exception.InsufficientFundsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class LedgerProcessorService {
    private static final Logger log = LoggerFactory.getLogger(LedgerProcessorService.class);

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final KafkaTemplate<String, PaymentResultEvent> resultKafkaTemplate;
    private final String resultTopic;

    public LedgerProcessorService(
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            KafkaTemplate<String, PaymentResultEvent> resultKafkaTemplate,
            @Value("${swiftpay.kafka.topics.payment-result}") String resultTopic
    ) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.resultKafkaTemplate = resultKafkaTemplate;
        this.resultTopic = resultTopic;
    }

    @KafkaListener(
            topics = "${swiftpay.kafka.topics.payment-initiated}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    @Transactional
    public void onPaymentInitiated(PaymentInitiatedEvent event) {
        log.info("Ledger received payment initiated event. txId={}, sender={}, receiver={}, amount={}, currency={}",
                event.getTransactionId(), event.getSenderAccountId(), event.getReceiverAccountId(),
                event.getAmount(), event.getCurrency());
        try {
            processWithLocks(event);
            publishResult(event.getTransactionId(), "SUCCESS", "Processed");
            log.info("Ledger processing completed successfully. txId={}", event.getTransactionId());
        } catch (AccountNotFoundException | InsufficientFundsException | IllegalArgumentException ex) {
            log.warn("Ledger business validation failed. txId={}, reason={}", event.getTransactionId(), ex.getMessage());
            publishResult(event.getTransactionId(), "FAILED", ex.getMessage());
        } catch (Exception ex) {
            log.error("Ledger processing failed due to unrecoverable error. txId={}, reason={}",
                    event.getTransactionId(), ex.getMessage(), ex);
            publishResult(event.getTransactionId(), "FAILED", "Unexpected processing error");
            throw ex;
        }
    }

    
    protected void processWithLocks(PaymentInitiatedEvent event) {
        log.info("Starting transactional ledger processing with pessimistic locks. txId={}", event.getTransactionId());
        if (event.getSenderAccountId().equals(event.getReceiverAccountId())) {
            throw new IllegalArgumentException("Sender and receiver must be different accounts");
        }

        String firstLockId = event.getSenderAccountId().compareTo(event.getReceiverAccountId()) < 0
                ? event.getSenderAccountId() : event.getReceiverAccountId();
        String secondLockId = event.getSenderAccountId().compareTo(event.getReceiverAccountId()) < 0
                ? event.getReceiverAccountId() : event.getSenderAccountId();

        Account firstLocked = accountRepository.findByIdForUpdate(firstLockId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstLockId));
        Account secondLocked = accountRepository.findByIdForUpdate(secondLockId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondLockId));

        Account sender = firstLocked.getId().equals(event.getSenderAccountId()) ? firstLocked : secondLocked;
        Account receiver = firstLocked.getId().equals(event.getReceiverAccountId()) ? firstLocked : secondLocked;

        validateFundsAndCurrency(sender, receiver, event.getAmount(), event.getCurrency());
        log.info("Validated account balances and currency for txId={}", event.getTransactionId());

        sender.setBalance(sender.getBalance().subtract(event.getAmount()));
        sender.setUpdatedAt(Instant.now());

        receiver.setBalance(receiver.getBalance().add(event.getAmount()));
        receiver.setUpdatedAt(Instant.now());

        accountRepository.save(sender);
        accountRepository.save(receiver);
        log.info("Persisted account balance updates. txId={}, senderBalance={}, receiverBalance={}",
                event.getTransactionId(), sender.getBalance(), receiver.getBalance());

        LedgerEntry debit = new LedgerEntry();
        debit.setTransactionId(event.getTransactionId());
        debit.setAccountId(sender.getId());
        debit.setEntryType(LedgerEntry.EntryType.DEBIT);
        debit.setAmount(event.getAmount());
        debit.setBalanceAfter(sender.getBalance());
        debit.setCreatedAt(Instant.now());

        LedgerEntry credit = new LedgerEntry();
        credit.setTransactionId(event.getTransactionId());
        credit.setAccountId(receiver.getId());
        credit.setEntryType(LedgerEntry.EntryType.CREDIT);
        credit.setAmount(event.getAmount());
        credit.setBalanceAfter(receiver.getBalance());
        credit.setCreatedAt(Instant.now());

        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);
        log.info("Persisted double-entry ledger records and committed transaction. txId={}", event.getTransactionId());
    }

    private void validateFundsAndCurrency(Account sender, Account receiver, BigDecimal amount, String currency) {
        if (!sender.getCurrency().equalsIgnoreCase(currency) || !receiver.getCurrency().equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("Currency mismatch between request and account setup");
        }
        if (sender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in sender account " + sender.getId());
        }
    }

    private void publishResult(String transactionId, String status, String reason) {
        PaymentResultEvent resultEvent = new PaymentResultEvent(transactionId, status, reason, Instant.now());
        resultKafkaTemplate.send(resultTopic, transactionId, resultEvent);
        log.info("Published payment result event. topic={}, txId={}, status={}", resultTopic, transactionId, status);
    }
}
