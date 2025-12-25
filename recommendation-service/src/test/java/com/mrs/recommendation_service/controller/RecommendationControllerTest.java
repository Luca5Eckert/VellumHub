package com.mrs.recommendation_service.controller;

import com.mrs.recommendation_service.model.Recommendation;
import com.mrs.recommendation_service.provider.UserAuthenticationProvider;
import com.mrs.recommendation_service.service.RecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecommendationController.class)
@DisplayName("RecommendationController Tests")
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private UserAuthenticationProvider userAuthenticationProvider;

    @Nested
    @DisplayName("GET /api/recommendations - Get Recommendations")
    class GetRecommendationsTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 and recommendations when user is authenticated")
        void getRecommendations_WhenAuthenticated_ReturnsRecommendations() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID mediaId1 = UUID.randomUUID();
            UUID mediaId2 = UUID.randomUUID();
            
            List<Recommendation> recommendations = List.of(
                    new Recommendation(mediaId1, List.of("ACTION", "THRILLER"), 0.8, 0.95, 0.9),
                    new Recommendation(mediaId2, List.of("ACTION"), 0.7, 0.87, 0.85)
            );

            when(userAuthenticationProvider.getUserId()).thenReturn(userId);
            when(recommendationService.get(userId)).thenReturn(recommendations);

            mockMvc.perform(get("/api/recommendations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].media_id").value(mediaId1.toString()))
                    .andExpect(jsonPath("$[1].media_id").value(mediaId2.toString()));

            verify(userAuthenticationProvider, times(1)).getUserId();
            verify(recommendationService, times(1)).get(userId);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return empty list when no recommendations available")
        void getRecommendations_WhenNoRecommendations_ReturnsEmptyList() throws Exception {
            UUID userId = UUID.randomUUID();

            when(userAuthenticationProvider.getUserId()).thenReturn(userId);
            when(recommendationService.get(userId)).thenReturn(List.of());

            mockMvc.perform(get("/api/recommendations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Should return 401 when not authenticated")
        void getRecommendations_WhenNotAuthenticated_ReturnsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/recommendations"))
                    .andExpect(status().isUnauthorized());

            verify(recommendationService, never()).get(any());
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 500 when user profile not found")
        void getRecommendations_WhenUserProfileNotFound_ReturnsError() throws Exception {
            UUID userId = UUID.randomUUID();

            when(userAuthenticationProvider.getUserId()).thenReturn(userId);
            when(recommendationService.get(userId))
                    .thenThrow(new RuntimeException("User profile not found"));

            mockMvc.perform(get("/api/recommendations"))
                    .andExpect(status().isInternalServerError());
        }
    }
}
