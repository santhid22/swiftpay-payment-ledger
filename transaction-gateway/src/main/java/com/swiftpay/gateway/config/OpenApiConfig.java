package com.swiftpay.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SwiftPay Transaction Gateway API")
                        .version("v1")
                        .description("Gateway endpoints for payment initiation with request validation, idempotency, and asynchronous processing.")
                        .contact(new Contact().name("SwiftPay Platform Team").email("platform@swiftpay.internal"))
                        .license(new License().name("Proprietary")));
    }
}
