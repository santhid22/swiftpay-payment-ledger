package com.swiftpay.ledger;

import com.swiftpay.ledger.model.Account;
import com.swiftpay.ledger.model.LedgerEntry;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.LedgerEntryRepository;
import com.swiftpay.shared.event.PaymentInitiatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class LedgerServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("swiftpay")
                    .withUsername("swiftpay")
                    .withPassword("swiftpay");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));


    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void shouldConsumeEventApplyLockedBalanceTransitionAndPersistDoubleEntryTrail() throws Exception {
        String senderId = "acct-sender-lock";
        String receiverId = "acct-receiver-lock";
        String txId = "ltx-" + UUID.randomUUID();

        Account sender = new Account();
        sender.setId(senderId);
        sender.setCurrency("USD");
        sender.setBalance(new BigDecimal("1000.00"));
        sender.setUpdatedAt(Instant.now());

        Account receiver = new Account();
        receiver.setId(receiverId);
        receiver.setCurrency("USD");
        receiver.setBalance(new BigDecimal("250.00"));
        receiver.setUpdatedAt(Instant.now());

        accountRepository.save(sender);
        accountRepository.save(receiver);

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                txId, senderId, receiverId, new BigDecimal("125.50"), "USD", Instant.now()
        );
        kafkaTemplate.send("swiftpay.payment.initiated", txId, event);

        long deadline = System.currentTimeMillis() + 30000;
        List<LedgerEntry> txEntries = List.of();
        while (System.currentTimeMillis() < deadline) {
            txEntries = ledgerEntryRepository.findByTransactionId(txId);
            if (txEntries.size() == 2) {
                break;
            }
            Thread.sleep(500);
        }

        assertThat(txEntries).hasSize(2);
        assertThat(txEntries.stream().anyMatch(e -> e.getEntryType() == LedgerEntry.EntryType.DEBIT)).isTrue();
        assertThat(txEntries.stream().anyMatch(e -> e.getEntryType() == LedgerEntry.EntryType.CREDIT)).isTrue();

        Account senderAfter = accountRepository.findById(senderId).orElseThrow();
        Account receiverAfter = accountRepository.findById(receiverId).orElseThrow();

        assertThat(senderAfter.getBalance()).isEqualByComparingTo(new BigDecimal("874.50"));
        assertThat(receiverAfter.getBalance()).isEqualByComparingTo(new BigDecimal("375.50"));
    }
}
