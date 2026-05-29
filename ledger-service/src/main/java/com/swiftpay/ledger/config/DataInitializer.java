package com.swiftpay.ledger.config;

import com.swiftpay.ledger.model.Account;
import com.swiftpay.ledger.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner seedAccounts(AccountRepository accountRepository) {
        return args -> {
            List<Account> seedAccounts = List.of(
                    buildAccount("acct-sender-001", "USD", "100000.00"),
                    buildAccount("acct-receiver-001", "USD", "1000.00"),
                    buildAccount("acct-receiver-009", "USD", "1000.00")
            );

            for (Account account : seedAccounts) {
                if (accountRepository.existsById(account.getId())) {
                    continue;
                }
                accountRepository.save(account);
                log.info("Seeded ledger account id={}, currency={}, balance={}",
                        account.getId(), account.getCurrency(), account.getBalance());
            }
        };
    }

    private Account buildAccount(String id, String currency, String balance) {
        Account account = new Account();
        account.setId(id);
        account.setCurrency(currency);
        account.setBalance(new BigDecimal(balance));
        account.setUpdatedAt(Instant.now());
        return account;
    }
}
