package com.mrs.catalog_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrs.catalog_service.dto.CreateMediaRequest;
import com.mrs.catalog_service.dto.GetMediaResponse;
import com.mrs.catalog_service.model.Genre;
import com.mrs.catalog_service.model.MediaType;
import com.mrs.catalog_service.service.MediaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MediaController.class)
@DisplayName("MediaController Tests")
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MediaService mediaService;

    @Nested
    @DisplayName("POST /media - Create Media")
    class CreateMediaTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 201 when creating media with valid data as ADMIN")
        void createMedia_WithValidDataAsAdmin_ReturnsCreated() throws Exception {
            CreateMediaRequest request = new CreateMediaRequest(
                    "Inception",
                    "A mind-bending thriller",
                    2010,
                    com.mrs.catalog_service.model.MediaType.MOVIE,
                    "http://example.com/cover.jpg",
                    List.of(Genre.ACTION, Genre.THRILLER)
            );

            doNothing().when(mediaService).create(any(CreateMediaRequest.class));

            mockMvc.perform(post("/media")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(mediaService, times(1)).create(any(CreateMediaRequest.class));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Should return 403 when user without ADMIN role tries to create media")
        void createMedia_WithoutAdminRole_ReturnsForbidden() throws Exception {
            CreateMediaRequest request = new CreateMediaRequest(
                    "Inception",
                    "A mind-bending thriller",
                    2010,
                    com.mrs.catalog_service.model.MediaType.MOVIE,
                    "http://example.com/cover.jpg",
                    List.of(Genre.ACTION)
            );

            mockMvc.perform(post("/media")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(mediaService, never()).create(any(CreateMediaRequest.class));
        }

        @Test
        @DisplayName("Should return 401 when creating media without authentication")
        void createMedia_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
            CreateMediaRequest request = new CreateMediaRequest(
                    "Inception",
                    "A mind-bending thriller",
                    2010,
                    com.mrs.catalog_service.model.MediaType.MOVIE,
                    "http://example.com/cover.jpg",
                    List.of(Genre.ACTION)
            );

            mockMvc.perform(post("/media")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /media - Delete Media")
    class DeleteMediaTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 200 when deleting media as ADMIN")
        void deleteMedia_AsAdmin_ReturnsOk() throws Exception {
            UUID mediaId = UUID.randomUUID();

            doNothing().when(mediaService).delete(mediaId);

            mockMvc.perform(delete("/media")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mediaId)))
                    .andExpect(status().isOk());

            verify(mediaService, times(1)).delete(mediaId);
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Should return 403 when user without ADMIN role tries to delete media")
        void deleteMedia_WithoutAdminRole_ReturnsForbidden() throws Exception {
            UUID mediaId = UUID.randomUUID();

            mockMvc.perform(delete("/media")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mediaId)))
                    .andExpect(status().isForbidden());

            verify(mediaService, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("GET /media/{id} - Get Media By ID")
    class GetMediaByIdTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 and media data when media exists")
        void getById_WhenMediaExists_ReturnsMedia() throws Exception {
            UUID mediaId = UUID.randomUUID();
            GetMediaResponse response = new GetMediaResponse(
                    mediaId,
                    "Inception",
                    "A mind-bending thriller",
                    2010,
                    com.mrs.catalog_service.model.MediaType.MOVIE,
                    List.of(Genre.ACTION, Genre.THRILLER)
            );

            when(mediaService.get(mediaId)).thenReturn(response);

            mockMvc.perform(get("/media/{id}", mediaId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(mediaId.toString()))
                    .andExpect(jsonPath("$.title").value("Inception"));

            verify(mediaService, times(1)).get(mediaId);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 500 when media not found")
        void getById_WhenMediaNotFound_ReturnsError() throws Exception {
            UUID mediaId = UUID.randomUUID();

            when(mediaService.get(mediaId)).thenThrow(new RuntimeException("Media not found"));

            mockMvc.perform(get("/media/{id}", mediaId))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Should return 401 when not authenticated")
        void getById_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
            UUID mediaId = UUID.randomUUID();

            mockMvc.perform(get("/media/{id}", mediaId))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /media - Get All Media")
    class GetAllMediaTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 and list of media")
        void getAll_ReturnsMediaList() throws Exception {
            UUID mediaId1 = UUID.randomUUID();
            UUID mediaId2 = UUID.randomUUID();
            List<GetMediaResponse> mediaList = List.of(
                    new GetMediaResponse(mediaId1, "Inception", "Thriller", 2010, com.mrs.catalog_service.model.MediaType.MOVIE, List.of(Genre.ACTION)),
                    new GetMediaResponse(mediaId2, "The Ring", "Horror", 2002, com.mrs.catalog_service.model.MediaType.MOVIE, List.of(Genre.HORROR))
            );

            when(mediaService.getAll(0, 10)).thenReturn(mediaList);

            mockMvc.perform(get("/media")
                            .param("pageNumber", "0")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].title").value("Inception"))
                    .andExpect(jsonPath("$[1].title").value("The Ring"));

            verify(mediaService, times(1)).getAll(0, 10);
        }

        @Test
        @WithMockUser
        @DisplayName("Should use default pagination values")
        void getAll_WithDefaultPagination_UsesDefaults() throws Exception {
            when(mediaService.getAll(0, 10)).thenReturn(List.of());

            mockMvc.perform(get("/media"))
                    .andExpect(status().isOk());

            verify(mediaService, times(1)).getAll(0, 10);
        }
    }
}
