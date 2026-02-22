package com.mrs.recommendation_service.share.config;

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
 * OpenAPI/Swagger configuration for Recommendation Service.
 * Provides comprehensive API documentation for personalized book recommendation endpoints.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI recommendationServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VellumHub Recommendation Service API")
                        .description("""
                                RESTful API for AI-powered personalized book recommendations in the VellumHub platform.
                                
                                ## Overview
                                This service provides intelligent book recommendations using:
                                - **Vector Similarity Search**: PostgreSQL pgvector extension for cosine similarity
                                - **User Profile Vectors**: Dynamically updated based on rating history
                                - **Genre-Based Embeddings**: 15-dimensional vectors representing book genres
                                
                                ## How It Works
                                1. User ratings are consumed from Kafka events
                                2. User preference vectors are updated in real-time
                                3. Recommendations are generated using cosine similarity between user preferences and book features
                                4. Results are enriched with book metadata from the Catalog Service
                                
                                ## Algorithm Details
                                - **Vector Dimensions**: 15 (one per genre: Fantasy, Sci-Fi, Horror, Romance, etc.)
                                - **Similarity Metric**: Cosine distance for accurate preference matching
                                - **Index Type**: HNSW (Hierarchical Navigable Small World) for fast approximate nearest neighbor search
                                - **Performance**: Typically under 100ms for datasets with 100K+ books
                                
                                ## Event Consumption
                                This service consumes events from:
                                - `created-book`: Create book feature vectors
                                - `updated-book`: Update book feature vectors
                                - `deleted-book`: Remove book feature vectors
                                - `rating-created`: Update user profile vectors
                                - `reading-status-updated`: Adjust user preference weights
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
                        .url("http://localhost:8085")
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
