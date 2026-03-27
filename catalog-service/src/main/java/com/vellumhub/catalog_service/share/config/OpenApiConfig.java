package com.mrs.catalog_service.share.config;

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
 * OpenAPI/Swagger configuration for Catalog Service.
 * Provides comprehensive API documentation for book catalog management endpoints.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI catalogServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VellumHub Catalog Service API")
                        .description("""
                                RESTful API for book catalog management in the VellumHub platform.
                                
                                ## Overview
                                This service manages the book catalog with the following capabilities:
                                - **Book Management**: CRUD operations for books with metadata (ISBN, author, genres, page count)
                                - **Book Requests**: User submission workflow for new books requiring admin approval
                                - **Book Progress**: Track reading status (TO_READ, READING, COMPLETED) and page progress
                                - **Bulk Operations**: Retrieve multiple books by IDs for recommendation enrichment
                                
                                ## Authorization
                                - Public endpoints: None (all require authentication)
                                - User endpoints: View books, submit book requests, manage reading progress
                                - Admin endpoints: Create/update/delete books, approve book requests
                                
                                ## Event Publishing
                                This service publishes events to Apache Kafka for:
                                - `book-created`: When a new book is added to the catalog
                                - `book-updated`: When book metadata is modified
                                - `book-deleted`: When a book is removed from the catalog
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
                        .url("http://localhost:8081")
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
