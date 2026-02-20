package com.mrs.catalog_service.module.book_request.application.controller;

import com.mrs.catalog_service.module.book_request.application.dto.BookRequestResponse;
import com.mrs.catalog_service.module.book_request.application.dto.CreateBookRequestDto;
import com.mrs.catalog_service.module.book_request.application.service.BookRequestApplicationService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/book-requests")
@Tag(name = "Book Requests", description = "Endpoints for managing book submission requests and approval workflow")
public class BookRequestController {

    private final BookRequestApplicationService bookRequestApplicationService;

    public BookRequestController(BookRequestApplicationService bookRequestApplicationService) {
        this.bookRequestApplicationService = bookRequestApplicationService;
    }

    @PostMapping
    @Operation(summary = "Submit book request", description = "Submits a new book for admin review and approval. Once approved, the book will be added to the catalog.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Book request submitted successfully",
                    content = @Content(schema = @Schema(implementation = BookRequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid book data provided", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<BookRequestResponse> create(
            @Valid @RequestBody CreateBookRequestDto request
    ) {
        BookRequestResponse response = bookRequestApplicationService.create(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("{requestId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve book request", description = "Approves a pending book request and adds the book to the catalog. Requires ADMIN role.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Book request approved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin permission required", content = @Content),
            @ApiResponse(responseCode = "404", description = "Book request not found", content = @Content)
    })
    public ResponseEntity<Void> approve(
            @Parameter(description = "Book request ID") @PathVariable(required = true) Long requestId
    ) {
        bookRequestApplicationService.approve(requestId);
        return ResponseEntity.noContent().build();
    }

}
