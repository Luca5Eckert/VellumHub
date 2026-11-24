package com.vellumhub.engagement_service.module.reading_diary.application;

import com.vellumhub.engagement_service.module.reading_diary.application.ReadingDiary.Cursor;
import com.vellumhub.engagement_service.module.reading_diary.application.ReadingDiary.Entries;
import com.vellumhub.engagement_service.module.reading_diary.application.ReadingDiary.Note;
import com.vellumhub.engagement_service.module.reading_diary.application.ReadingDiary.NoteType;
import com.vellumhub.engagement_service.module.reading_diary.infrastructure.CatalogReadingAccess;
import com.vellumhub.engagement_service.module.reading_diary.infrastructure.ReadingDiaryStore;
import com.vellumhub.security.jwt.JwtAccessToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping("/readings")
@Tag(name = "Reading Diary")
public class ReadingDiaryController {
    private final ReadingDiaryStore store;
    private final CatalogReadingAccess catalog;

    public ReadingDiaryController(ReadingDiaryStore store, CatalogReadingAccess catalog) {
        this.store = store;
        this.catalog = catalog;
    }

    @GetMapping("/{readingId}/entries")
    @SecurityRequirement(name = "oauth2", scopes = {"engagement:read", "catalog:personal:read"})
    @Operation(summary = "List private reading diary entries")
    public Entries listEntries(@PathVariable UUID readingId, @RequestParam(required = false) String cursor,
                               @RequestParam(defaultValue = "20") int limit, @AuthenticationPrincipal Jwt jwt) {
        validateLimit(limit);
        Cursor position = decode(cursor);
        UUID userId = JwtAccessToken.userId(jwt);
        catalog.pageCount(readingId, jwt.getTokenValue());
        var entries = store.entries(userId, readingId, position, limit + 1);
        if (entries.size() <= limit) {
            return new Entries(entries, null);
        }
        var last = entries.get(limit - 1);
        return new Entries(entries.subList(0, limit), encode(new Cursor(last.readAt(), last.id())));
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 100");
        }
    }

    @PostMapping("/{readingId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "oauth2", scopes = {"engagement:write", "catalog:personal:read"})
    @Operation(summary = "Create a private reading note or quote")
    public Note createNote(@PathVariable UUID readingId, @Valid @RequestBody NoteRequest request,
                           @AuthenticationPrincipal Jwt jwt) {
        Note note = checked(UUID.randomUUID(), request, readingId, jwt);
        if (note.readAt() == null) {
            note = new Note(note.id(), note.type(), note.page(), note.text(), Instant.now());
        }
        return store.create(JwtAccessToken.userId(jwt), readingId, note);
    }

    @PatchMapping("/{readingId}/notes/{noteId}")
    @SecurityRequirement(name = "oauth2", scopes = {"engagement:write", "catalog:personal:read"})
    @Operation(summary = "Edit a private reading note or quote")
    public Note editNote(@PathVariable UUID readingId, @PathVariable UUID noteId,
                         @Valid @RequestBody EditNoteRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (request.readAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A note's reading time cannot be edited");
        }
        return store.update(JwtAccessToken.userId(jwt), readingId, checked(noteId, new NoteRequest(request.type(), request.page(), request.text(), null), readingId, jwt))
                .orElseThrow(ReadingDiaryController::notFound);
    }

    @DeleteMapping("/{readingId}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "oauth2", scopes = {"engagement:write"})
    @Operation(summary = "Delete a private reading note or quote")
    public void deleteNote(@PathVariable UUID readingId, @PathVariable UUID noteId, @AuthenticationPrincipal Jwt jwt) {
        if (!store.delete(JwtAccessToken.userId(jwt), readingId, noteId)) {
            throw notFound();
        }
    }

    private Note checked(UUID noteId, NoteRequest request, UUID readingId, Jwt jwt) {
        int pages = catalog.pageCount(readingId, jwt.getTokenValue());
        if (request.page() != null && request.page() > pages) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page exceeds book page count");
        }
        if (request.readAt() != null && request.readAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reading time cannot be in the future");
        }
        return new Note(noteId, request.type(), request.page(), request.text().strip(), request.readAt());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found");
    }

    private static String encode(Cursor cursor) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (cursor.time() + "," + cursor.id()).getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (value.length() > 160) {
                throw new IllegalArgumentException();
            }
            var parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split(",", -1);
            if (parts.length != 2 || !parts[1].matches("p:[0-9]{1,19}|n:[0-9a-f-]{36}")) {
                throw new IllegalArgumentException();
            }
            var time = Instant.parse(parts[0]);
            if (time.isBefore(Instant.parse("0001-01-01T00:00:00Z")) || time.isAfter(Instant.now())) {
                throw new IllegalArgumentException();
            }
            return new Cursor(time, parts[1]);
        } catch (IllegalArgumentException | DateTimeParseException malformed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid diary cursor");
        }
    }

    public record EditNoteRequest(@NotNull NoteType type, @PositiveOrZero Integer page,
                                  @NotBlank @Size(max = 2000) String text, @Schema(hidden = true) Instant readAt) {}

    public record NoteRequest(@NotNull NoteType type, @PositiveOrZero Integer page,
                              @NotBlank @Size(max = 2000) String text, Instant readAt) {}
}
