package com.vellumhub.catalog_service.module.book_progress.application.controller;

import com.vellumhub.catalog_service.module.book_progress.application.dto.BookProgressResponse;
import com.vellumhub.catalog_service.module.book_progress.application.dto.BookReadingResponse;
import com.vellumhub.catalog_service.module.book_progress.application.handler.BookReadingsHandler;
import com.vellumhub.catalog_service.module.book_progress.application.dto.BookStatusRequest;
import com.vellumhub.catalog_service.module.book_progress.application.dto.UpdateBookProgressRequest;
import com.vellumhub.catalog_service.module.book_progress.application.handler.DefineBookStatusHandler;
import com.vellumhub.catalog_service.module.book_progress.application.handler.DeleteBookProgressHandler;
import com.vellumhub.catalog_service.module.book_progress.application.handler.GetReadingListHandler;
import com.vellumhub.catalog_service.module.book_progress.application.handler.UpdateBookProgressHandler;
import com.vellumhub.catalog_service.share.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/book-progress")
@Tag(
        name = "Book Progress",
        description = "Endpoints for managing book reading progress and status tracking"
)
public class BookProgressController {

    private final DefineBookStatusHandler defineBookStatusHandler;
    private final UpdateBookProgressHandler updateBookProgressHandler;
    private final DeleteBookProgressHandler deleteBookProgressHandler;
    private final GetReadingListHandler getReadingListHandler;
    private final BookReadingsHandler bookReadingsHandler;
    private final AuthenticationService authenticationService;

    public BookProgressController(
            DefineBookStatusHandler defineBookStatusHandler,
            UpdateBookProgressHandler updateBookProgressHandler,
            DeleteBookProgressHandler deleteBookProgressHandler,
            GetReadingListHandler getReadingListHandler,
            BookReadingsHandler bookReadingsHandler,
            AuthenticationService authenticationService
    ) {
        this.defineBookStatusHandler = defineBookStatusHandler;
        this.updateBookProgressHandler = updateBookProgressHandler;
        this.deleteBookProgressHandler = deleteBookProgressHandler;
        this.getReadingListHandler = getReadingListHandler;
        this.bookReadingsHandler = bookReadingsHandler;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/{bookId}/readings")
    @SecurityRequirement(name = "oauth2", scopes = {"catalog:personal:write"})
    @Operation(summary = "Start a new reading")
    @ApiResponse(responseCode = "201", description = "Reading started")
    @ApiResponse(responseCode = "404", description = "Book not found", content = @Content)
    @ApiResponse(responseCode = "409", description = "An active reading already exists", content = @Content)
    public ResponseEntity<BookReadingResponse> startReading(@PathVariable UUID bookId,
            @RequestBody(required = false) StartReadingRequest request) {
        var reading = bookReadingsHandler.start(bookId, authenticationService.getAuthenticatedUserId(),
                request == null ? null : request.startedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(toReadingResponse(reading));
    }

    @GetMapping("/readings/{readingId}/book")
    @SecurityRequirement(name = "oauth2", scopes = {"catalog:personal:read"})
    @Operation(summary = "Get book information for an owned reading")
    @ApiResponse(responseCode = "404", description = "Reading not found", content = @Content)
    public ReadingBookResponse getReadingBook(@PathVariable UUID readingId) {
        return new ReadingBookResponse(bookReadingsHandler.pageCount(readingId,
                authenticationService.getAuthenticatedUserId()));
    }

    public record ReadingBookResponse(int pageCount) {}

    @GetMapping("/{bookId}/readings")
    @SecurityRequirement(name = "oauth2", scopes = {"catalog:personal:read"})
    @Operation(summary = "List the authenticated user's readings for a book, newest first")
    public List<BookReadingResponse> listReadings(@PathVariable UUID bookId) {
        return bookReadingsHandler.list(bookId, authenticationService.getAuthenticatedUserId()).stream()
                .map(this::toReadingResponse)
                .toList();
    }

    @PatchMapping("/{bookId}/readings/{readingId}/progress")
    @SecurityRequirement(name = "oauth2", scopes = {"catalog:personal:write"})
    @Operation(summary = "Record a page or percentage for a reading")
    public BookReadingResponse recordProgress(@PathVariable UUID bookId, @PathVariable UUID readingId,
                                               @RequestBody RecordProgressRequest request) {
        return toReadingResponse(bookReadingsHandler.progress(bookId, authenticationService.getAuthenticatedUserId(),
                readingId, request.page(), request.percentage(), request.readAt()));
    }

    @PatchMapping("/{bookId}/readings/{readingId}/status")
    @SecurityRequirement(name = "oauth2", scopes = {"catalog:personal:write"})
    @Operation(summary = "Transition the status of a reading")
    public BookReadingResponse transition(@PathVariable UUID bookId, @PathVariable UUID readingId,
                                           @Valid @RequestBody TransitionReadingRequest request) {
        return toReadingResponse(bookReadingsHandler.transition(bookId, authenticationService.getAuthenticatedUserId(),
                readingId, request.status()));
    }

    @PatchMapping("/{bookId}/readings/{readingId}/target")
    @SecurityRequirement(name = "oauth2", scopes = {"catalog:personal:write"})
    @Operation(summary = "Set the reading target date, or clear it with null")
    public BookReadingResponse target(@PathVariable UUID bookId, @PathVariable UUID readingId,
                                      @RequestBody ReadingTargetRequest request) {
        return toReadingResponse(bookReadingsHandler.target(bookId, authenticationService.getAuthenticatedUserId(),
                readingId, request.targetEndDate()));
    }

    public record StartReadingRequest(OffsetDateTime startedAt) {}

    public record RecordProgressRequest(Integer page, Integer percentage, OffsetDateTime readAt) {}

    public record TransitionReadingRequest(@NotNull ReadingStatus status) {}

    public record ReadingTargetRequest(LocalDate targetEndDate) {}

    private BookReadingResponse toReadingResponse(BookReadingsHandler.Reading reading) {
        var progress = reading.progress();
        return new BookReadingResponse(progress.getId(), reading.readingNumber(), progress.getReadingStatus(),
                progress.getStartedAt(), progress.getEndAt(), progress.getCurrentPage(), progress.getBook().getPageCount(),
                progress.getTargetEndDate());
    }

    @PostMapping("/{bookId}/status")
    @SecurityRequirement(name = "oauth2", scopes = {"catalog:personal:write"})
    @Operation(
            summary = "Set book status",
            description = "Sets the reading status for a book (WANT_TO_READ, READING, COMPLETED)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Book status defined successfully",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid status value",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Book not found",
                    content = @Content)
    })
    public ResponseEntity<Void> defineBookStatus(
            @Parameter(description = "Book ID") @PathVariable UUID bookId,
            @Valid @RequestBody BookStatusRequest request
    ) {
        UUID userId = authenticationService.getAuthenticatedUserId();
        defineBookStatusHandler.handle(request, userId, bookId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{bookId}/progress")
    @SecurityRequirement(name = "oauth2", scopes = {"catalog:personal:write"})
    @Operation(
            summary = "Update reading progress",
            description = "Updates the current page number for a book being read"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reading progress updated successfully",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid page number",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Book progress record not found",
                    content = @Content)
    })
    public ResponseEntity<Void> updateBookProgress(
            @Parameter(description = "Book ID") @PathVariable UUID bookId,
            @Valid @RequestBody UpdateBookProgressRequest request
    ) {
        UUID userId = authenticationService.getAuthenticatedUserId();
        updateBookProgressHandler.handle(request.newCurrentPage(), bookId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{bookId}")
    @SecurityRequirement(name = "oauth2", scopes = {"catalog:personal:write"})
    @Operation(
            summary = "Remove book progress",
            description = "Removes a book from the user's reading list and deletes progress tracking"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Book progress deleted successfully",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Book progress record not found",
                    content = @Content)
    })
    public ResponseEntity<Void> deleteBookProgress(
            @Parameter(description = "Book ID") @PathVariable UUID bookId
    ) {
        UUID userId = authenticationService.getAuthenticatedUserId();
        deleteBookProgressHandler.handle(bookId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reading-list")
    @SecurityRequirement(name = "oauth2", scopes = {"catalog:personal:read"})
    @Operation(
            summary = "Get reading list",
            description = "Retrieves the authenticated user's complete reading list with progress information"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reading list retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BookProgressResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content)
    })
    public ResponseEntity<List<BookProgressResponse>> getReadingList() {
        UUID userId = authenticationService.getAuthenticatedUserId();
        return ResponseEntity.ok(getReadingListHandler.handle(userId));
    }

}