package com.mrs.catalog_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrs.catalog_service.application.controller.MediaController;
import com.mrs.catalog_service.application.dto.CreateMediaRequest;
import com.mrs.catalog_service.application.dto.GetMediaResponse;
import com.mrs.catalog_service.application.dto.UpdateMediaRequest;
import com.mrs.catalog_service.application.exception.MediaApplicationException;
import com.mrs.catalog_service.domain.exception.MediaNotFoundException;
import com.mrs.catalog_service.domain.model.Genre;
import com.mrs.catalog_service.domain.model.MediaType;
import com.mrs.catalog_service.domain.service.MediaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for MediaController.
 * Uses @WebMvcTest for isolated controller layer testing with MockMvc.
 * Tests cover all HTTP endpoints, validation, authentication, and error handling.
 */
@WebMvcTest(MediaController.class)
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private MediaService mediaService;

    @Nested
    @DisplayName("POST /media - Create Media")
    class CreateMediaTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("whenValidRequest_shouldReturnCreated")
        void whenValidRequest_shouldReturnCreated() throws Exception {
            // Arrange
            CreateMediaRequest request = new CreateMediaRequest(
                    "The Matrix",
                    "A computer hacker learns about the true nature of reality",
                    1999,
                    MediaType.MOVIE,
                    "https://example.com/matrix.jpg",
                    List.of(Genre.ACTION, Genre.SCIFI)
            );

            doNothing().when(mediaService).create(any(CreateMediaRequest.class));

            // Act & Assert
            mockMvc.perform(post("/media")
                            .with(csrf())
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(mediaService, times(1)).create(any(CreateMediaRequest.class));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("whenNonAdminUser_shouldReturnForbidden")
        void whenNonAdminUser_shouldReturnForbidden() throws Exception {
            // Arrange
            CreateMediaRequest request = new CreateMediaRequest(
                    "The Matrix",
                    "Description",
                    1999,
                    MediaType.MOVIE,
                    "https://example.com/matrix.jpg",
                    List.of(Genre.ACTION)
            );

            // Act & Assert
            mockMvc.perform(post("/media")
                            .with(csrf())
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(mediaService, never()).create(any(CreateMediaRequest.class));
        }

        @Test
        @DisplayName("whenUnauthenticated_shouldReturnUnauthorized")
        void whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
            // Arrange
            CreateMediaRequest request = new CreateMediaRequest(
                    "The Matrix",
                    "Description",
                    1999,
                    MediaType.MOVIE,
                    "https://example.com/matrix.jpg",
                    List.of(Genre.ACTION)
            );

            // Act & Assert
            mockMvc.perform(post("/media")
                            .with(csrf())
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(mediaService, never()).create(any(CreateMediaRequest.class));
        }
    }

    @Nested
    @DisplayName("GET /media/{id} - Get Media By ID")
    class GetMediaByIdTests {

        @Test
        @WithMockUser
        @DisplayName("whenValidId_shouldReturnMedia")
        void whenValidId_shouldReturnMedia() throws Exception {
            // Arrange
            UUID mediaId = UUID.randomUUID();
            Instant now = Instant.now();

            GetMediaResponse expectedResponse = new GetMediaResponse(
                    mediaId,
                    "The Matrix",
                    "A computer hacker learns about the true nature of reality",
                    1999,
                    MediaType.MOVIE,
                    "https://example.com/matrix.jpg",
                    List.of(Genre.ACTION, Genre.SCIFI),
                    now,
                    now
            );

            when(mediaService.get(mediaId)).thenReturn(expectedResponse);

            // Act & Assert
            mockMvc.perform(get("/media/{id}", mediaId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(mediaId.toString()))
                    .andExpect(jsonPath("$.title").value("The Matrix"))
                    .andExpect(jsonPath("$.releaseYear").value(1999))
                    .andExpect(jsonPath("$.mediaType").value("MOVIE"));

            verify(mediaService, times(1)).get(mediaId);
        }

        @Test
        @WithMockUser
        @DisplayName("whenMediaNotFound_shouldReturnNotFound")
        void whenMediaNotFound_shouldReturnNotFound() throws Exception {
            // Arrange
            UUID mediaId = UUID.randomUUID();
            when(mediaService.get(mediaId)).thenThrow(new MediaNotFoundException("Media not found"));

            // Act & Assert
            mockMvc.perform(get("/media/{id}", mediaId))
                    .andExpect(status().isNotFound());

            verify(mediaService, times(1)).get(mediaId);
        }

        @Test
        @WithMockUser
        @DisplayName("whenInvalidUuidFormat_shouldReturnBadRequest")
        void whenInvalidUuidFormat_shouldReturnBadRequest() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/media/{id}", "invalid-uuid"))
                    .andExpect(status().isBadRequest());

            verify(mediaService, never()).get(any(UUID.class));
        }

        @Test
        @DisplayName("whenUnauthenticated_shouldReturnUnauthorized")
        void whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
            // Arrange
            UUID mediaId = UUID.randomUUID();

            // Act & Assert
            mockMvc.perform(get("/media/{id}", mediaId))
                    .andExpect(status().isUnauthorized());

            verify(mediaService, never()).get(any(UUID.class));
        }
    }

    @Nested
    @DisplayName("GET /media - Get All Media")
    class GetAllMediaTests {

        @Test
        @WithMockUser
        @DisplayName("whenValidRequest_shouldReturnMediaList")
        void whenValidRequest_shouldReturnMediaList() throws Exception {
            // Arrange
            Instant now = Instant.now();
            List<GetMediaResponse> mediaList = List.of(
                    new GetMediaResponse(UUID.randomUUID(), "The Matrix", "Desc 1", 1999,
                            MediaType.MOVIE, "url1", List.of(Genre.ACTION), now, now),
                    new GetMediaResponse(UUID.randomUUID(), "Breaking Bad", "Desc 2", 2008,
                            MediaType.SERIES, "url2", List.of(Genre.DRAMA), now, now)
            );

            when(mediaService.getAll(0, 10)).thenReturn(mediaList);

            // Act & Assert
            mockMvc.perform(get("/media")
                            .param("pageNumber", "0")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].title").value("The Matrix"))
                    .andExpect(jsonPath("$[1].title").value("Breaking Bad"));

            verify(mediaService, times(1)).getAll(0, 10);
        }

        @Test
        @WithMockUser
        @DisplayName("whenDefaultPagination_shouldUseDefaults")
        void whenDefaultPagination_shouldUseDefaults() throws Exception {
            // Arrange
            when(mediaService.getAll(0, 10)).thenReturn(List.of());

            // Act & Assert
            mockMvc.perform(get("/media"))
                    .andExpect(status().isOk());

            verify(mediaService, times(1)).getAll(0, 10);
        }

        @Test
        @WithMockUser
        @DisplayName("whenEmptyList_shouldReturnEmptyArray")
        void whenEmptyList_shouldReturnEmptyArray() throws Exception {
            // Arrange
            when(mediaService.getAll(0, 10)).thenReturn(List.of());

            // Act & Assert
            mockMvc.perform(get("/media"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("PUT /media/{id} - Update Media")
    class UpdateMediaTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("whenValidRequest_shouldReturnOk")
        void whenValidRequest_shouldReturnOk() throws Exception {
            // Arrange
            UUID mediaId = UUID.randomUUID();
            UpdateMediaRequest request = new UpdateMediaRequest(
                    "Updated Title",
                    "Updated Description",
                    2000,
                    "https://example.com/updated.jpg",
                    List.of(Genre.ACTION, Genre.DRAMA)
            );

            doNothing().when(mediaService).update(eq(mediaId), any(UpdateMediaRequest.class));

            // Act & Assert
            mockMvc.perform(put("/media/{id}", mediaId)
                            .with(csrf())
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(mediaService, times(1)).update(eq(mediaId), any(UpdateMediaRequest.class));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("whenMediaNotFound_shouldReturnNotFound")
        void whenMediaNotFound_shouldReturnNotFound() throws Exception {
            // Arrange
            UUID mediaId = UUID.randomUUID();
            UpdateMediaRequest request = new UpdateMediaRequest(
                    "Updated Title",
                    "Updated Description",
                    2000,
                    "https://example.com/updated.jpg",
                    List.of(Genre.ACTION)
            );

            doThrow(new MediaNotFoundException("Media not found"))
                    .when(mediaService).update(eq(mediaId), any(UpdateMediaRequest.class));

            // Act & Assert
            mockMvc.perform(put("/media/{id}", mediaId)
                            .with(csrf())
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("whenNonAdminUser_shouldReturnForbidden")
        void whenNonAdminUser_shouldReturnForbidden() throws Exception {
            // Arrange
            UUID mediaId = UUID.randomUUID();
            UpdateMediaRequest request = new UpdateMediaRequest(
                    "Updated Title",
                    "Updated Description",
                    2000,
                    "https://example.com/updated.jpg",
                    List.of(Genre.ACTION)
            );

            // Act & Assert
            mockMvc.perform(put("/media/{id}", mediaId)
                            .with(csrf())
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(mediaService, never()).update(any(UUID.class), any(UpdateMediaRequest.class));
        }
    }

    @Nested
    @DisplayName("DELETE /media/{id} - Delete Media")
    class DeleteMediaTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("whenValidId_shouldReturnNoContent")
        void whenValidId_shouldReturnNoContent() throws Exception {
            // Arrange
            UUID mediaId = UUID.randomUUID();
            doNothing().when(mediaService).delete(mediaId);

            // Act & Assert
            mockMvc.perform(delete("/media/{id}", mediaId)
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            verify(mediaService, times(1)).delete(mediaId);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("whenMediaNotFound_shouldReturnNotFound")
        void whenMediaNotFound_shouldReturnNotFound() throws Exception {
            // Arrange
            UUID mediaId = UUID.randomUUID();
            doThrow(new MediaNotFoundException("Media not found")).when(mediaService).delete(mediaId);

            // Act & Assert
            mockMvc.perform(delete("/media/{id}", mediaId)
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("whenNonAdminUser_shouldReturnForbidden")
        void whenNonAdminUser_shouldReturnForbidden() throws Exception {
            // Arrange
            UUID mediaId = UUID.randomUUID();

            // Act & Assert
            mockMvc.perform(delete("/media/{id}", mediaId)
                            .with(csrf()))
                    .andExpect(status().isForbidden());

            verify(mediaService, never()).delete(any(UUID.class));
        }

        @Test
        @DisplayName("whenUnauthenticated_shouldReturnUnauthorized")
        void whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
            // Arrange
            UUID mediaId = UUID.randomUUID();

            // Act & Assert
            mockMvc.perform(delete("/media/{id}", mediaId)
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());

            verify(mediaService, never()).delete(any(UUID.class));
        }
    }
}
