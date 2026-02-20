package com.mrs.engagement_service.share.config;

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
 * OpenAPI/Swagger configuration for Engagement Service.
 * Provides comprehensive API documentation for rating and user engagement endpoints.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI engagementServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VellumHub Engagement Service API")
                        .description("""
                                RESTful API for managing user engagement with books in the VellumHub platform.
                                
                                ## Overview
                                This service handles all user engagement operations including:
                                - **Star Ratings**: Rate books from 0 to 5 stars with optional text reviews
                                - **Rating History**: Retrieve and filter user rating history
                                - **Rating Updates**: Modify existing ratings
                                
                                ## Rating System
                                - Ratings range from 0 to 5 stars (supports half-star precision)
                                - Each rating can include an optional text review
                                - Ratings are used by the Recommendation Service for personalized suggestions
                                
                                ## Event Publishing
                                This service publishes events to Apache Kafka for:
                                - `rating-created`: When a user submits a new rating
                                - `reading-status-updated`: When reading progress changes
                                
                                ## Filtering Options
                                Rating queries support filtering by:
                                - Minimum/maximum star rating
                                - Date range (from/to timestamps)
                                - Pagination (page number and size)
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
                        .url("http://localhost:8083")
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
