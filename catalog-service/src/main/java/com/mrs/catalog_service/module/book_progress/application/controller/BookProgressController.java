package com.mrs.catalog_service.module.book_progress.application.controller;

import com.mrs.catalog_service.module.book_progress.application.dto.UpdateBookProgressRequest;
import com.mrs.catalog_service.share.service.AuthenticationService;
import com.mrs.catalog_service.module.book_progress.application.dto.BookProgressResponse;
import com.mrs.catalog_service.module.book_progress.application.dto.BookStatusRequest;
import com.mrs.catalog_service.module.book_progress.application.handler.DefineBookStatusHandler;
import com.mrs.catalog_service.module.book_progress.application.handler.DeleteBookProgressHandler;
import com.mrs.catalog_service.module.book_progress.application.handler.GetReadingListHandler;
import com.mrs.catalog_service.module.book_progress.application.handler.UpdateBookProgressHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    private final AuthenticationService authenticationService;

    public BookProgressController(DefineBookStatusHandler defineBookStatusHandler, UpdateBookProgressHandler updateBookProgressHandler, DeleteBookProgressHandler deleteBookProgressHandler, GetReadingListHandler getReadingListHandler, AuthenticationService authenticationService) {
        this.defineBookStatusHandler = defineBookStatusHandler;
        this.updateBookProgressHandler = updateBookProgressHandler;
        this.deleteBookProgressHandler = deleteBookProgressHandler;
        this.getReadingListHandler = getReadingListHandler;
        this.authenticationService = authenticationService;
    }


    @PostMapping("/{bookId}/status")
    @Operation(summary = "Set book status", description = "Sets the reading status for a book (TO_READ, READING, COMPLETED)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Book status set successfully",
                    content = @Content(schema = @Schema(implementation = BookProgressResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid status value", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Book not found", content = @Content)
    })
    public ResponseEntity<BookProgressResponse> defineBookStatus(
            @Parameter(description = "Book ID") @PathVariable(value = "bookId") UUID bookId,
            @Valid @RequestBody BookStatusRequest request
    ) {
        UUID userId = authenticationService.getAuthenticatedUserId();

        var response = defineBookStatusHandler.handle(
                request,
                userId,
                bookId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{bookId}/progress")
    @Operation(summary = "Update reading progress", description = "Updates the current page number for a book being read")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reading progress updated successfully",
                    content = @Content(schema = @Schema(implementation = BookProgressResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid page number", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Book progress record not found", content = @Content)
    })
    public ResponseEntity<BookProgressResponse> updateBookProgress(
            @Parameter(description = "Book ID") @PathVariable(value = "bookId") UUID bookId,
            @Valid @RequestBody UpdateBookProgressRequest request
    ) {
        UUID userId = authenticationService.getAuthenticatedUserId();

        var response = updateBookProgressHandler.handle(
                request.newCurrentPage(),
                bookId,
                userId
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @DeleteMapping("/{bookId}")
    @Operation(summary = "Remove book progress", description = "Removes a book from the user's reading list and deletes progress tracking")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Book progress deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Book progress record not found", content = @Content)
    })
    public ResponseEntity<Void> deleteBookProgress(
            @Parameter(description = "Book ID") @PathVariable(value = "bookId") UUID bookId
    ) {
        UUID userId = authenticationService.getAuthenticatedUserId();

        deleteBookProgressHandler.handle(
                bookId,
                userId
        );

        return ResponseEntity.ok().build();
    }

    @GetMapping("/reading-list")
    @Operation(summary = "Get reading list", description = "Retrieves the authenticated user's complete reading list with progress information")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reading list retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BookProgressResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<List<BookProgressResponse>> getReadingList() {
        UUID userId = authenticationService.getAuthenticatedUserId();

        var response = getReadingListHandler.handle(userId);

        return ResponseEntity.ok(response);

    }



}
