package com.vellumhub.engagement_service.module.reading_diary.application;

import com.vellumhub.engagement_service.module.reading_diary.infrastructure.CatalogReadingAccess;
import com.vellumhub.engagement_service.module.reading_diary.infrastructure.ReadingDiaryStore;
import com.vellumhub.engagement_service.share.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.MethodParameter;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReadingDiaryControllerTest {
    private final UUID reader = UUID.randomUUID();
    private final UUID reading = UUID.randomUUID();
    private final ReadingDiaryStore store = mock(ReadingDiaryStore.class);
    private final CatalogReadingAccess catalog = mock(CatalogReadingAccess.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Jwt user = Jwt.withTokenValue("fixture").header("alg", "RS256").subject(reader.toString()).build();
        mvc = MockMvcBuilders.standaloneSetup(new ReadingDiaryController(store, catalog))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, WebDataBinderFactory binder) {
                        return user;
                    }
                }).build();
    }

    @Test
    void rejectsBlankOrOversizedTextBeforeOwnershipLookup() throws Exception {
        postNote("   ", HttpStatus.BAD_REQUEST);
        postNote("a".repeat(2001), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(catalog, store);
    }

    @Test
    void hidesReadingsOwnedByAnotherUser() throws Exception {
        when(catalog.pageCount(reading, "fixture")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/readings/{readingId}/entries", reading)).andExpect(status().isNotFound());
        postNote("A valid note", HttpStatus.NOT_FOUND);
        verify(store).hasEntries(reader, reading);
        verifyNoMoreInteractions(store);
    }

    @Test
    void ownershipOutageDoesNotCreateANote() throws Exception {
        when(catalog.pageCount(reading, "fixture")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        postNote("A valid note", HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(store);
    }

    @Test
    void rejectsMalformedCursorBeforeOwnershipLookup() throws Exception {
        mvc.perform(get("/readings/{readingId}/entries", reading).param("cursor", "!"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(catalog, store);
    }

    @Test
    void listsOwnDiaryAndAllowsDeletingNoteAfterReadingRemoval() throws Exception {
        UUID note = UUID.randomUUID();
        when(catalog.pageCount(reading, "fixture")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        when(store.hasEntries(reader, reading)).thenReturn(true);
        when(store.entries(reader, reading, null, 21)).thenReturn(java.util.List.of());
        when(store.noteReadingIds(reader, null, 20)).thenReturn(java.util.List.of(reading));
        when(store.delete(reader, reading, note)).thenReturn(true);
        mvc.perform(get("/readings/with-notes")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0]")
                        .value(reading.toString()));
        mvc.perform(get("/readings/{readingId}/entries", reading)).andExpect(status().isOk());
        mvc.perform(delete("/readings/{readingId}/notes/{noteId}", reading, note)).andExpect(status().isNoContent());
    }

    @Test
    void rejectsChangingTheDiarySortKey() throws Exception {
        mvc.perform(patch("/readings/{readingId}/notes/{noteId}", reading, UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {"type":"NOTE","text":"Edited","readAt":"2025-01-01T12:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(catalog, store);
    }

    private void postNote(String text, HttpStatus expected) throws Exception {
        mvc.perform(post("/readings/{readingId}/notes", reading).contentType("application/json")
                        .content("{\"type\":\"NOTE\",\"text\":\"" + text + "\"}"))
                .andExpect(status().is(expected.value()));
    }
}
