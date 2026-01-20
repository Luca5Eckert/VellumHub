package com.mrs.recommendation_service.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI recommendationServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Recommendation Service API")
                        .description("API para fornecimento de recomendações personalizadas de mídia")
                        .version("1.0.0"))
                .addServersItem(new Server()
                        .url("http://localhost:8084")
                        .description("Servidor de Desenvolvimento"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Token JWT para autenticação. Use o formato: Bearer {token}")));
    }
}
