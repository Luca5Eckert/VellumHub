package com.mrs.engagement_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrs.engagement_service.dto.InteractionCreateRequest;
import com.mrs.engagement_service.model.InteractionType;
import com.mrs.engagement_service.service.EngagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EngagementController.class)
@DisplayName("EngagementController Tests")
class EngagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EngagementService engagementService;

    @Nested
    @DisplayName("POST /engagement - Create Engagement")
    class CreateEngagementTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 201 when creating engagement with valid data")
        void createEngagement_WithValidData_ReturnsCreated() throws Exception {
            InteractionCreateRequest request = new InteractionCreateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    InteractionType.LIKE,
                    1.0
            );

            doNothing().when(engagementService).create(any(InteractionCreateRequest.class));

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
        @DisplayName("Should return 400 when userId is null")
        void createEngagement_WithNullUserId_ReturnsBadRequest() throws Exception {
            String jsonWithNullUserId = """
                    {
                        "userId": null,
                        "mediaId": "123e4567-e89b-12d3-a456-426614174000",
                        "type": "LIKE",
                        "interactionValue": 1.0
                    }
                    """;

            mockMvc.perform(post("/engagement")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonWithNullUserId))
                    .andExpect(status().isBadRequest());

            verify(engagementService, never()).create(any());
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 when mediaId is null")
        void createEngagement_WithNullMediaId_ReturnsBadRequest() throws Exception {
            String jsonWithNullMediaId = """
                    {
                        "userId": "123e4567-e89b-12d3-a456-426614174000",
                        "mediaId": null,
                        "type": "LIKE",
                        "interactionValue": 1.0
                    }
                    """;

            mockMvc.perform(post("/engagement")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonWithNullMediaId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 when type is null")
        void createEngagement_WithNullType_ReturnsBadRequest() throws Exception {
            String jsonWithNullType = """
                    {
                        "userId": "123e4567-e89b-12d3-a456-426614174000",
                        "mediaId": "123e4567-e89b-12d3-a456-426614174001",
                        "type": null,
                        "interactionValue": 1.0
                    }
                    """;

            mockMvc.perform(post("/engagement")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonWithNullType))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 401 when not authenticated")
        void createEngagement_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
            InteractionCreateRequest request = new InteractionCreateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    InteractionType.LIKE,
                    1.0
            );

            mockMvc.perform(post("/engagement")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("Should accept LIKE interaction type")
        void createEngagement_WithLikeType_ReturnsCreated() throws Exception {
            InteractionCreateRequest request = new InteractionCreateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    InteractionType.LIKE,
                    1.0
            );

            doNothing().when(engagementService).create(any(InteractionCreateRequest.class));

            mockMvc.perform(post("/engagement")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser
        @DisplayName("Should accept DISLIKE interaction type")
        void createEngagement_WithDislikeType_ReturnsCreated() throws Exception {
            InteractionCreateRequest request = new InteractionCreateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    InteractionType.DISLIKE,
                    -1.0
            );

            doNothing().when(engagementService).create(any(InteractionCreateRequest.class));

            mockMvc.perform(post("/engagement")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser
        @DisplayName("Should accept WATCH interaction type")
        void createEngagement_WithWatchType_ReturnsCreated() throws Exception {
            InteractionCreateRequest request = new InteractionCreateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    InteractionType.WATCH,
                    0.5
            );

            doNothing().when(engagementService).create(any(InteractionCreateRequest.class));

            mockMvc.perform(post("/engagement")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }
}
