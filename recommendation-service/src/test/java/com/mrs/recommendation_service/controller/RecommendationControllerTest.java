package com.mrs.recommendation_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrs.recommendation_service.application.controller.RecommendationController;
import com.mrs.recommendation_service.domain.model.Recommendation;
import com.mrs.recommendation_service.domain.service.RecommendationService;
import com.mrs.recommendation_service.infrastructure.provider.UserAuthenticationProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for RecommendationController.
 * Uses @WebMvcTest for isolated controller layer testing with MockMvc.
 * Tests cover all HTTP endpoints, authentication, and response handling.
 */
@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private RecommendationService recommendationService;

    @MockitoBean
    private UserAuthenticationProvider userAuthenticationProvider;

    @Nested
    @DisplayName("GET /api/recommendations - Get Recommendations")
    class GetRecommendationsTests {

        @Test
        @WithMockUser
        @DisplayName("whenValidUser_shouldReturnRecommendations")
        void whenValidUser_shouldReturnRecommendations() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId1 = UUID.randomUUID();
            UUID mediaId2 = UUID.randomUUID();

            List<Recommendation> recommendations = List.of(
                    new Recommendation(
                            mediaId1,
                            List.of("ACTION", "SCIFI"),
                            85.5,
                            92.3,
                            88.0
                    ),
                    new Recommendation(
                            mediaId2,
                            List.of("DRAMA", "COMEDY"),
                            78.2,
                            85.1,
                            81.5
                    )
            );

            when(userAuthenticationProvider.getUserId()).thenReturn(userId);
            when(recommendationService.get(userId)).thenReturn(recommendations);

            // Act & Assert
            mockMvc.perform(get("/api/recommendations"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].media_id").value(mediaId1.toString()))
                    .andExpect(jsonPath("$[0].genres").isArray())
                    .andExpect(jsonPath("$[0].genres.length()").value(2))
                    .andExpect(jsonPath("$[0].popularity_score").value(85.5))
                    .andExpect(jsonPath("$[0].recommendation_score").value(92.3))
                    .andExpect(jsonPath("$[0].content_score").value(88.0))
                    .andExpect(jsonPath("$[1].media_id").value(mediaId2.toString()));

            verify(userAuthenticationProvider, times(1)).getUserId();
            verify(recommendationService, times(1)).get(userId);
        }

        @Test
        @WithMockUser
        @DisplayName("whenNoRecommendations_shouldReturnEmptyList")
        void whenNoRecommendations_shouldReturnEmptyList() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();

            when(userAuthenticationProvider.getUserId()).thenReturn(userId);
            when(recommendationService.get(userId)).thenReturn(List.of());

            // Act & Assert
            mockMvc.perform(get("/api/recommendations"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(0));

            verify(recommendationService, times(1)).get(userId);
        }

        @Test
        @WithMockUser
        @DisplayName("whenSingleRecommendation_shouldReturnSingleItemList")
        void whenSingleRecommendation_shouldReturnSingleItemList() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            List<Recommendation> recommendations = List.of(
                    new Recommendation(
                            mediaId,
                            List.of("ACTION"),
                            95.0,
                            98.5,
                            96.0
                    )
            );

            when(userAuthenticationProvider.getUserId()).thenReturn(userId);
            when(recommendationService.get(userId)).thenReturn(recommendations);

            // Act & Assert
            mockMvc.perform(get("/api/recommendations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].media_id").value(mediaId.toString()))
                    .andExpect(jsonPath("$[0].popularity_score").value(95.0));
        }

        @Test
        @WithMockUser
        @DisplayName("whenRecommendationWithMultipleGenres_shouldReturnAllGenres")
        void whenRecommendationWithMultipleGenres_shouldReturnAllGenres() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            List<Recommendation> recommendations = List.of(
                    new Recommendation(
                            mediaId,
                            List.of("ACTION", "SCIFI", "THRILLER", "ADVENTURE"),
                            85.5,
                            92.3,
                            88.0
                    )
            );

            when(userAuthenticationProvider.getUserId()).thenReturn(userId);
            when(recommendationService.get(userId)).thenReturn(recommendations);

            // Act & Assert
            mockMvc.perform(get("/api/recommendations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].genres.length()").value(4))
                    .andExpect(jsonPath("$[0].genres[0]").value("ACTION"))
                    .andExpect(jsonPath("$[0].genres[1]").value("SCIFI"))
                    .andExpect(jsonPath("$[0].genres[2]").value("THRILLER"))
                    .andExpect(jsonPath("$[0].genres[3]").value("ADVENTURE"));
        }

        @Test
        @DisplayName("whenUnauthenticated_shouldReturnUnauthorized")
        void whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/recommendations"))
                    .andExpect(status().isUnauthorized());

            verify(recommendationService, never()).get(any(UUID.class));
            verify(userAuthenticationProvider, never()).getUserId();
        }

        @Test
        @WithMockUser
        @DisplayName("whenRecommendationWithNullScores_shouldHandleGracefully")
        void whenRecommendationWithNullScores_shouldHandleGracefully() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            List<Recommendation> recommendations = List.of(
                    new Recommendation(
                            mediaId,
                            List.of("ACTION"),
                            null,   // null popularity score
                            null,   // null recommendation score
                            null    // null content score
                    )
            );

            when(userAuthenticationProvider.getUserId()).thenReturn(userId);
            when(recommendationService.get(userId)).thenReturn(recommendations);

            // Act & Assert
            mockMvc.perform(get("/api/recommendations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].media_id").value(mediaId.toString()))
                    .andExpect(jsonPath("$[0].popularity_score").doesNotExist())
                    .andExpect(jsonPath("$[0].recommendation_score").doesNotExist())
                    .andExpect(jsonPath("$[0].content_score").doesNotExist());
        }

        @Test
        @WithMockUser
        @DisplayName("whenRecommendationWithEmptyGenres_shouldReturnEmptyGenresList")
        void whenRecommendationWithEmptyGenres_shouldReturnEmptyGenresList() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            List<Recommendation> recommendations = List.of(
                    new Recommendation(
                            mediaId,
                            List.of(),  // empty genres
                            85.5,
                            92.3,
                            88.0
                    )
            );

            when(userAuthenticationProvider.getUserId()).thenReturn(userId);
            when(recommendationService.get(userId)).thenReturn(recommendations);

            // Act & Assert
            mockMvc.perform(get("/api/recommendations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].genres").isArray())
                    .andExpect(jsonPath("$[0].genres.length()").value(0));
        }
    }
}
