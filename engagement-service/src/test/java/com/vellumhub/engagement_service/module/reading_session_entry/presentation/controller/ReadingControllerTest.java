package com.vellumhub.engagement_service.module.reading_session_entry.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.reading_session_entry.application.use_case.CompleteReadingSessionUseCase;
import com.vellumhub.engagement_service.module.reading_session_entry.application.use_case.GetReadingHistoryUseCase;
import com.vellumhub.engagement_service.module.reading_session_entry.application.use_case.StartReadingSessionUseCase;
import com.vellumhub.engagement_service.module.reading_session_entry.application.use_case.UpdateReadingProgressUseCase;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.model.ReadingSessionEntry;
import com.vellumhub.engagement_service.module.reading_session_entry.presentation.dto.UpdateReadingProgressRequest;
import com.vellumhub.engagement_service.share.security.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadingController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2Vz"
})
class ReadingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StartReadingSessionUseCase startReadingSessionUseCase;

    @MockBean
    private UpdateReadingProgressUseCase updateReadingProgressUseCase;

    @MockBean
    private CompleteReadingSessionUseCase completeReadingSessionUseCase;

    @MockBean
    private GetReadingHistoryUseCase getReadingHistoryUseCase;

    @Test
    @DisplayName("Should require authentication for reading endpoints")
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/reading/" + UUID.randomUUID() + "/start"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Should start reading session")
    void shouldStartReadingSession() throws Exception {
        UUID bookId = UUID.randomUUID();

        mockMvc.perform(post("/reading/" + bookId + "/start"))
                .andExpect(status().isCreated());

        verify(startReadingSessionUseCase).execute(bookId);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Should update reading progress")
    void shouldUpdateReadingProgress() throws Exception {
        UUID bookId = UUID.randomUUID();
        UpdateReadingProgressRequest request = new UpdateReadingProgressRequest(90);

        mockMvc.perform(put("/reading/" + bookId + "/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(updateReadingProgressUseCase).execute(bookId, 90);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Should validate progress request body")
    void shouldValidateProgressRequestBody() throws Exception {
        UUID bookId = UUID.randomUUID();
        UpdateReadingProgressRequest request = new UpdateReadingProgressRequest(-1);

        mockMvc.perform(put("/reading/" + bookId + "/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Should complete reading session")
    void shouldCompleteReadingSession() throws Exception {
        UUID bookId = UUID.randomUUID();

        mockMvc.perform(post("/reading/" + bookId + "/complete"))
                .andExpect(status().isOk());

        verify(completeReadingSessionUseCase).execute(bookId);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Should return reading history")
    void shouldReturnReadingHistory() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        BookSnapshot snapshot = new BookSnapshot();
        snapshot.setBookId(bookId);
        snapshot.setPageCount(200);

        List<ReadingSessionEntry> history = List.of(
                ReadingSessionEntry.started(UUID.randomUUID(), userId, snapshot),
                ReadingSessionEntry.progressed(UUID.randomUUID(), userId, snapshot, 50)
        );

        when(getReadingHistoryUseCase.execute(bookId)).thenReturn(history);

        mockMvc.perform(get("/reading/" + bookId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventType").value("STARTED"))
                .andExpect(jsonPath("$[1].currentPage").value(50));
    }
}
