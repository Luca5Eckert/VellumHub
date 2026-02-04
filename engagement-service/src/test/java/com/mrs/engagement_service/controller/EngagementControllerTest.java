package com.mrs.engagement_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrs.engagement_service.application.controller.EngagementController;
import com.mrs.engagement_service.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.application.dto.InteractionCreateRequest;
import com.mrs.engagement_service.application.dto.InteractionGetResponse;
import com.mrs.engagement_service.domain.model.InteractionType;
import com.mrs.engagement_service.domain.service.EngagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for EngagementController.
 * Uses @WebMvcTest for isolated controller layer testing with MockMvc.
 * Tests cover all HTTP endpoints, validation, authentication, and error handling.
 */
@WebMvcTest(EngagementController.class)
class EngagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private EngagementService engagementService;

    @Nested
    @DisplayName("POST /engagement - Create Engagement")
    class CreateEngagementTests {

        @Test
        @WithMockUser
        @DisplayName("whenValidRequest_shouldReturnCreated")
        void whenValidRequest_shouldReturnCreated() throws Exception {
            // Arrange
            InteractionCreateRequest request = new InteractionCreateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    InteractionType.LIKE,
                    1.0
            );

            doNothing().when(engagementService).create(any(InteractionCreateRequest.class));

            // Act & Assert
            mockMvc.perform(post("/engagement")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().string("Engagement registered with success"));

            verify(engagementService, times(1)).create(any(InteractionCreateRequest.class));
        }

        @Test
        @WithMockUser
        @DisplayName("whenNullUserId_shouldReturnBadRequest")
        void whenNullUserId_shouldReturnBadRequest() throws Exception {
            // Arrange - Null userId should fail validation
            String invalidRequest = """
                {
                    "userId": null,
                    "mediaId": "550e8400-e29b-41d4-a716-446655440000",
                    "type": "LIKE",
                    "interactionValue": 1.0
                }
                """;

            // Act & Assert
            mockMvc.perform(post("/engagement")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());

            verify(engagementService, never()).create(any(InteractionCreateRequest.class));
        }

        @Test
        @WithMockUser
        @DisplayName("whenNullMediaId_shouldReturnBadRequest")
        void whenNullMediaId_shouldReturnBadRequest() throws Exception {
            // Arrange - Null mediaId should fail validation
            String invalidRequest = """
                {
                    "userId": "550e8400-e29b-41d4-a716-446655440000",
                    "mediaId": null,
                    "type": "LIKE",
                    "interactionValue": 1.0
                }
                """;

            // Act & Assert
            mockMvc.perform(post("/engagement")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());

            verify(engagementService, never()).create(any(InteractionCreateRequest.class));
        }

        @Test
        @WithMockUser
        @DisplayName("whenNullInteractionType_shouldReturnBadRequest")
        void whenNullInteractionType_shouldReturnBadRequest() throws Exception {
            // Arrange - Null type should fail validation
            String invalidRequest = """
                {
                    "userId": "550e8400-e29b-41d4-a716-446655440000",
                    "mediaId": "550e8400-e29b-41d4-a716-446655440001",
                    "type": null,
                    "interactionValue": 1.0
                }
                """;

            // Act & Assert
            mockMvc.perform(post("/engagement")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());

            verify(engagementService, never()).create(any(InteractionCreateRequest.class));
        }

        @Test
        @WithMockUser
        @DisplayName("whenRatingInteraction_shouldReturnCreated")
        void whenRatingInteraction_shouldReturnCreated() throws Exception {
            // Arrange - Rating interaction with value
            InteractionCreateRequest request = new InteractionCreateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    InteractionType.RATING,
                    4.5
            );

            doNothing().when(engagementService).create(any(InteractionCreateRequest.class));

            // Act & Assert
            mockMvc.perform(post("/engagement")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(engagementService, times(1)).create(any(InteractionCreateRequest.class));
        }

        @Test
        @DisplayName("whenUnauthenticated_shouldReturnUnauthorized")
        void whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
            // Arrange
            InteractionCreateRequest request = new InteractionCreateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    InteractionType.LIKE,
                    1.0
            );

            // Act & Assert
            mockMvc.perform(post("/engagement")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(engagementService, never()).create(any(InteractionCreateRequest.class));
        }
    }

    @Nested
    @DisplayName("GET /engagement/user/{userId} - Get User Interactions")
    class GetUserInteractionsTests {

        @Test
        @WithMockUser
        @DisplayName("whenValidUserId_shouldReturnInteractionList")
        void whenValidUserId_shouldReturnInteractionList() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            List<InteractionGetResponse> interactions = List.of(
                    new InteractionGetResponse(1L, userId, UUID.randomUUID(), InteractionType.LIKE, 1.0, now),
                    new InteractionGetResponse(2L, userId, UUID.randomUUID(), InteractionType.VIEW, 1.0, now)
            );

            when(engagementService.findAllOfUser(eq(userId), any(), any(), any(), eq(0), eq(10)))
                    .thenReturn(interactions);

            // Act & Assert
            mockMvc.perform(get("/engagement/user/{userId}", userId)
                            .param("pageNumber", "0")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].type").value("LIKE"))
                    .andExpect(jsonPath("$[1].type").value("VIEW"));

            verify(engagementService, times(1))
                    .findAllOfUser(eq(userId), any(), any(), any(), eq(0), eq(10));
        }

        @Test
        @WithMockUser
        @DisplayName("whenTypeFilter_shouldFilterByType")
        void whenTypeFilter_shouldFilterByType() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            List<InteractionGetResponse> interactions = List.of(
                    new InteractionGetResponse(1L, userId, UUID.randomUUID(), InteractionType.LIKE, 1.0, now)
            );

            when(engagementService.findAllOfUser(eq(userId), eq(InteractionType.LIKE), any(), any(), eq(0), eq(10)))
                    .thenReturn(interactions);

            // Act & Assert
            mockMvc.perform(get("/engagement/user/{userId}", userId)
                            .param("type", "LIKE")
                            .param("pageNumber", "0")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].type").value("LIKE"));

            verify(engagementService, times(1))
                    .findAllOfUser(eq(userId), eq(InteractionType.LIKE), any(), any(), eq(0), eq(10));
        }

        @Test
        @WithMockUser
        @DisplayName("whenNoInteractions_shouldReturnEmptyList")
        void whenNoInteractions_shouldReturnEmptyList() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();

            when(engagementService.findAllOfUser(eq(userId), any(), any(), any(), eq(0), eq(10)))
                    .thenReturn(List.of());

            // Act & Assert
            mockMvc.perform(get("/engagement/user/{userId}", userId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @WithMockUser
        @DisplayName("whenInvalidUuidFormat_shouldReturnBadRequest")
        void whenInvalidUuidFormat_shouldReturnBadRequest() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/engagement/user/{userId}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verify(engagementService, never())
                    .findAllOfUser(any(UUID.class), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("whenUnauthenticated_shouldReturnUnauthorized")
        void whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
            // Arrange
            UUID userId = UUID.randomUUID();

            // Act & Assert
            mockMvc.perform(get("/engagement/user/{userId}", userId))
                    .andExpect(status().isUnauthorized());

            verify(engagementService, never())
                    .findAllOfUser(any(UUID.class), any(), any(), any(), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("GET /engagement/media/{mediaId}/stats - Get Media Stats")
    class GetMediaStatsTests {

        @Test
        @WithMockUser
        @DisplayName("whenValidMediaId_shouldReturnMediaStats")
        void whenValidMediaId_shouldReturnMediaStats() throws Exception {
            // Arrange
            UUID mediaId = UUID.randomUUID();

            GetMediaStatusResponse expectedResponse = new GetMediaStatusResponse(
                    mediaId,
                    1000L,      // totalViews
                    500L,       // totalLikes
                    50L,        // totalDislikes
                    4.5,        // averageRating
                    200L,       // totalRatings
                    1750L,      // totalInteractions
                    85.5        // popularityScore
            );

            when(engagementService.getMediaStatus(mediaId)).thenReturn(expectedResponse);

            // Act & Assert
            mockMvc.perform(get("/engagement/media/{mediaId}/stats", mediaId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.mediaId").value(mediaId.toString()))
                    .andExpect(jsonPath("$.totalViews").value(1000))
                    .andExpect(jsonPath("$.totalLikes").value(500))
                    .andExpect(jsonPath("$.totalDislikes").value(50))
                    .andExpect(jsonPath("$.averageRating").value(4.5))
                    .andExpect(jsonPath("$.totalRatings").value(200))
                    .andExpect(jsonPath("$.totalInteractions").value(1750))
                    .andExpect(jsonPath("$.popularityScore").value(85.5));

            verify(engagementService, times(1)).getMediaStatus(mediaId);
        }

        @Test
        @WithMockUser
        @DisplayName("whenMediaHasNoInteractions_shouldReturnZeroStats")
        void whenMediaHasNoInteractions_shouldReturnZeroStats() throws Exception {
            // Arrange
            UUID mediaId = UUID.randomUUID();

            GetMediaStatusResponse expectedResponse = new GetMediaStatusResponse(
                    mediaId,
                    0L,         // totalViews
                    0L,         // totalLikes
                    0L,         // totalDislikes
                    0.0,        // averageRating
                    0L,         // totalRatings
                    0L,         // totalInteractions
                    0.0         // popularityScore
            );

            when(engagementService.getMediaStatus(mediaId)).thenReturn(expectedResponse);

            // Act & Assert
            mockMvc.perform(get("/engagement/media/{mediaId}/stats", mediaId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalViews").value(0))
                    .andExpect(jsonPath("$.totalInteractions").value(0));

            verify(engagementService, times(1)).getMediaStatus(mediaId);
        }

        @Test
        @WithMockUser
        @DisplayName("whenInvalidUuidFormat_shouldReturnBadRequest")
        void whenInvalidUuidFormat_shouldReturnBadRequest() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/engagement/media/{mediaId}/stats", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verify(engagementService, never()).getMediaStatus(any(UUID.class));
        }

        @Test
        @DisplayName("whenUnauthenticated_shouldReturnUnauthorized")
        void whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
            // Arrange
            UUID mediaId = UUID.randomUUID();

            // Act & Assert
            mockMvc.perform(get("/engagement/media/{mediaId}/stats", mediaId))
                    .andExpect(status().isUnauthorized());

            verify(engagementService, never()).getMediaStatus(any(UUID.class));
        }
    }
}
