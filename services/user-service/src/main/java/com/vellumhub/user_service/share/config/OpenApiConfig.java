package com.vellumhub.user_service.share.config;

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
    @org.springframework.beans.factory.annotation.Value("${JWT_ISSUER:https://auth.vellumhub.local}")
    private String issuer;

    private SecurityScheme oauthScheme() {
        var scopes = new io.swagger.v3.oas.models.security.Scopes();
        com.vellumhub.security.jwt.OAuthScopes.API.stream().sorted()
                .forEach(scope -> scopes.addString(scope, "Explicit API capability: " + scope));
        return new SecurityScheme().type(SecurityScheme.Type.OAUTH2)
                .flows(new io.swagger.v3.oas.models.security.OAuthFlows().authorizationCode(
                        new io.swagger.v3.oas.models.security.OAuthFlow()
                                .authorizationUrl(issuer + "/oauth2/authorize")
                                .tokenUrl(issuer + "/oauth2/token").scopes(scopes)));
    }


    @Bean
    public OpenAPI userServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VellumHub User Service API")
                        .description("""
                                RESTful profile API. Authentication and credentials belong exclusively to Auth.
                                
                                ## Overview
                                This service handles all user-related operations including:
                                - **Provisioning**: Private idempotent profile creation by Auth
                                - **Authorization**: Auth-issued access tokens, explicit scopes and USER/ADMIN roles
                                - **User Management**: CRUD operations for user profiles
                                - **Role-Based Access Control**: USER and ADMIN role management
                                
                                ## Authentication
                                All protected endpoints require a valid JWT token in the Authorization header.
                                Obtain tokens from Auth using Authorization Code + PKCE S256; see each operation for required scopes.
                                
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
                .addSecurityItem(new SecurityRequirement().addList("oauth2"))
                .components(new Components()
                        .addSecuritySchemes("oauth2", oauthScheme())
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT authentication token. Format: Bearer {token}")));
    }
}
