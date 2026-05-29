package com.swiftpay.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SwiftPay Ledger Service API")
                        .version("v1")
                        .description("Ledger reporting and transaction processing APIs with pessimistic locking and double-entry tracking.")
                        .contact(new Contact().name("SwiftPay Platform Team").email("platform@swiftpay.internal"))
                        .license(new License().name("Proprietary")));
    }
}
