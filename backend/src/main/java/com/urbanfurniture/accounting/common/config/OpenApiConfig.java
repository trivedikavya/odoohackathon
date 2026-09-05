package com.urbanfurniture.accounting.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI accountingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Urban Furniture - Accounting API")
                        .version("v1")
                        .description("""
                                Double-entry accounting API.
                                Every financial transaction posts a balanced journal entry
                                (SUM(debit) == SUM(credit)), validated server-side.
                                All reports are computed live from the ledger."""))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
