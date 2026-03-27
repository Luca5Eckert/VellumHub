package com.mrs.user_service.share.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for User Service.
 * Provides comprehensive API documentation for authentication and user management endpoints.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VellumHub User Service API")
                        .description("""
                                RESTful API for user management and authentication in the VellumHub platform.
                                
                                ## Overview
                                This service handles all user-related operations including:
                                - **User Registration**: Create new user accounts with email verification
                                - **Authentication**: Secure JWT-based authentication system
                                - **User Management**: CRUD operations for user profiles
                                - **Role-Based Access Control**: USER and ADMIN role management
                                
                                ## Authentication
                                All protected endpoints require a valid JWT token in the Authorization header.
                                Obtain a token via the `/auth/login` endpoint after registration.
                                
                                ## Rate Limiting
                                API requests are subject to rate limiting. Please refer to response headers for current limits.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("VellumHub Development Team")
                                .url("https://github.com/Luca5Eckert/VellumHub")
                                .email("support@vellumhub.dev"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .externalDocs(new ExternalDocumentation()
                        .description("VellumHub Documentation")
                        .url("https://github.com/Luca5Eckert/VellumHub#readme"))
                .addServersItem(new Server()
                        .url("http://localhost:8084")
                        .description("Development Server"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT authentication token. Format: Bearer {token}")));
    }
}
