package com.swiftpay.gateway;

import com.swiftpay.gateway.model.OutboundTransaction;
import com.swiftpay.gateway.repository.TransactionRepository;
import com.swiftpay.shared.event.PaymentInitiatedEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer; // Re-added for Redis container
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getRecords;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class TransactionGatewayIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("swiftpay")
                    .withUsername("swiftpay")
                    .withPassword("swiftpay");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    // Added Redis container to fix the RedisConnectionFailureException
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // Maps Spring's Redis properties to the container's random local port
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    private Consumer<String, PaymentInitiatedEvent> paymentInitiatedConsumer;

    @BeforeEach
    void setUpKafkaConsumer() {
        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "gateway-integration-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class,
                JsonDeserializer.TRUSTED_PACKAGES, "com.swiftpay.shared.event",
                JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentInitiatedEvent.class.getName(),
                JsonDeserializer.USE_TYPE_INFO_HEADERS, false
        );

        paymentInitiatedConsumer = new DefaultKafkaConsumerFactory<String, PaymentInitiatedEvent>(consumerProps)
                .createConsumer();
        paymentInitiatedConsumer.subscribe(Collections.singletonList("swiftpay.payment.initiated"));

        try {
            paymentInitiatedConsumer.poll(Duration.ofMillis(500));
        } catch (Exception ignored) {
        }
    }

    @AfterEach
    void tearDown() {
        if (paymentInitiatedConsumer != null) {
            paymentInitiatedConsumer.close();
        }
    }

    @Test
    void shouldPersistPendingPublishKafkaAndEnforceIdempotency() throws Exception {
        String txId = "itx-" + UUID.randomUUID();
        String payload = """
                {
                  "transactionId":"%s",
                  "senderAccountId":"acct-sender-001",
                  "receiverAccountId":"acct-receiver-001",
                  "amount":125.50,
                  "currency":"USD"
                }
                """.formatted(txId);

        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted());

        Optional<OutboundTransaction> pending = transactionRepository.findByTransactionId(txId);
        assertThat(pending).isPresent();
        assertThat(pending.get().getStatus()).isEqualTo("PENDING");

        PaymentInitiatedEvent initiatedEvent = null;
        long deadline = System.currentTimeMillis() + 15000;

        while (System.currentTimeMillis() < deadline && initiatedEvent == null) {
            ConsumerRecords<String, PaymentInitiatedEvent> records = getRecords(paymentInitiatedConsumer, Duration.ofMillis(1000));
            for (ConsumerRecord<String, PaymentInitiatedEvent> record : records.records("swiftpay.payment.initiated")) {
                if (record.value() != null && txId.equals(record.value().getTransactionId())) {
                    initiatedEvent = record.value();
                    break;
                }
            }
        }

        assertThat(initiatedEvent).withFailMessage("Kafka failed to receive the expected Event within timeout limits.").isNotNull();
        assertThat(initiatedEvent.getTransactionId()).isEqualTo(txId);
        assertThat(initiatedEvent.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(125.50));

        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted());

        List<OutboundTransaction> all = transactionRepository.findAll().stream()
                .filter(t -> txId.equals(t.getTransactionId()))
                .toList();

        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo("PENDING");
    }
}
