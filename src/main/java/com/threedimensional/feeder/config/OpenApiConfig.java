package com.threedimensional.feeder.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * OpenAPI 3 / Swagger documentation configuration for development.
 * <p>
 * Confined strictly to the {@code dev} profile via {@code @Profile("dev")}, ensuring documentation
 * beans are never registered in production. Configures global API metadata and Bearer JWT
 * authentication so developers can authorize and invoke secured endpoints directly from Swagger UI.
 */
@Configuration
@Profile("dev")
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Alimentador Automático API")
                        .description("API REST para monitoramento e agendamento de alimentadores automáticos de pets.")
                        .version("v1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Insira o token JWT retornado pelo endpoint /auth/google")));
    }
}
