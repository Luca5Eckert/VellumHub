package com.vellumhub.catalog_service.module.book_progress.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.catalog_service.module.book_progress.application.dto.BookProgressResponse;
import com.vellumhub.catalog_service.module.book_progress.application.dto.BookStatusRequest;
import com.vellumhub.catalog_service.module.book_progress.application.dto.UpdateBookProgressRequest;
import com.vellumhub.catalog_service.module.book_progress.application.handler.DefineBookStatusHandler;
import com.vellumhub.catalog_service.module.book_progress.application.handler.DeleteBookProgressHandler;
import com.vellumhub.catalog_service.module.book_progress.application.handler.GetReadingListHandler;
import com.vellumhub.catalog_service.module.book_progress.application.handler.UpdateBookProgressHandler;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressDomainException;
import com.vellumhub.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.vellumhub.catalog_service.share.service.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookProgressController.class)
class BookProgressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DefineBookStatusHandler defineBookStatusHandler;

    @MockitoBean
    private UpdateBookProgressHandler updateBookProgressHandler;

    @MockitoBean
    private DeleteBookProgressHandler deleteBookProgressHandler;

    @MockitoBean
    private GetReadingListHandler getReadingListHandler;

    @MockitoBean
    private AuthenticationService authenticationService;

    private UUID userId;
    private UUID bookId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        bookId = UUID.randomUUID();
        when(authenticationService.getAuthenticatedUserId()).thenReturn(userId);
    }

    @Nested
    class DefineBookStatus {

        @Test
        void shouldReturn201WhenStatusIsDefined() throws Exception {
            BookStatusRequest request = new BookStatusRequest(ReadingStatus.READING, 0, null, null);

            mockMvc.perform(post("/book-progress/{bookId}/status", bookId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(defineBookStatusHandler).handle(any(BookStatusRequest.class), eq(userId), eq(bookId));
        }

        @Test
        void shouldReturn404WhenBookNotFound() throws Exception {
            BookStatusRequest request = new BookStatusRequest(ReadingStatus.READING, 0, null, null);
            doThrow(new BookNotFoundException("Book not found"))
                    .when(defineBookStatusHandler).handle(any(), any(), any());

            mockMvc.perform(post("/book-progress/{bookId}/status", bookId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn409WhenUserAlreadyHasBookInReadingStatus() throws Exception {
            BookStatusRequest request = new BookStatusRequest(ReadingStatus.READING, 0, null, null);
            doThrow(new BookProgressDomainException("User already has a book in READING status"))
                    .when(defineBookStatusHandler).handle(any(), any(), any());

            mockMvc.perform(post("/book-progress/{bookId}/status", bookId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        void shouldReturn400WhenRequestBodyIsInvalid() throws Exception {
            mockMvc.perform(post("/book-progress/{bookId}/status", bookId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class UpdateBookProgress {

        @Test
        void shouldReturn200WhenProgressIsUpdated() throws Exception {
            UpdateBookProgressRequest request = new UpdateBookProgressRequest(120);

            mockMvc.perform(put("/book-progress/{bookId}/progress", bookId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(updateBookProgressHandler).handle(eq(120), eq(bookId), eq(userId));
        }

        @Test
        void shouldReturn404WhenProgressRecordNotFound() throws Exception {
            UpdateBookProgressRequest request = new UpdateBookProgressRequest(120);
            doThrow(new BookProgressDomainException("Progress not found"))
                    .when(updateBookProgressHandler).handle(any(), any(), any());

            mockMvc.perform(put("/book-progress/{bookId}/progress", bookId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class DeleteBookProgress {

        @Test
        void shouldReturn204WhenProgressIsDeleted() throws Exception {
            mockMvc.perform(delete("/book-progress/{bookId}", bookId))
                    .andExpect(status().isNoContent());

            verify(deleteBookProgressHandler).handle(eq(bookId), eq(userId));
        }

        @Test
        void shouldReturn404WhenProgressRecordNotFound() throws Exception {
            doThrow(new BookProgressDomainException("Progress not found"))
                    .when(deleteBookProgressHandler).handle(any(), any());

            mockMvc.perform(delete("/book-progress/{bookId}", bookId))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class GetReadingList {

        @Test
        void shouldReturn200WithReadingList() throws Exception {
            List<BookProgressResponse> readingList = List.of(
                    new BookProgressResponse(bookId, bookId, ReadingStatus.READING, 42),
                    new BookProgressResponse(UUID.randomUUID(), bookId, ReadingStatus.WANT_TO_READ, 0)
            );
            when(getReadingListHandler.handle(userId)).thenReturn(readingList);

            mockMvc.perform(get("/book-progress/reading-list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].readingStatus").value("READING"))
                    .andExpect(jsonPath("$[0].currentPage").value(42));
        }

        @Test
        void shouldReturn200WithEmptyListWhenNoProgress() throws Exception {
            when(getReadingListHandler.handle(userId)).thenReturn(List.of());

            mockMvc.perform(get("/book-progress/reading-list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void shouldUseAuthenticatedUserIdFromService() throws Exception {
            when(getReadingListHandler.handle(any())).thenReturn(List.of());

            mockMvc.perform(get("/book-progress/reading-list"));

            verify(authenticationService).getAuthenticatedUserId();
            verify(getReadingListHandler).handle(userId);
        }
    }
}