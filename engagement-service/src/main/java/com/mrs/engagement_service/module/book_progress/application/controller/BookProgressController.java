package com.mrs.engagement_service.module.book_progress.application.controller;

import com.mrs.engagement_service.infrastructure.service.AuthenticationService;
import com.mrs.engagement_service.module.book_progress.application.dto.BookProgressResponse;
import com.mrs.engagement_service.module.book_progress.application.dto.BookStatusRequest;
import com.mrs.engagement_service.module.book_progress.application.handler.DefineBookStatusHandler;
import com.mrs.engagement_service.module.book_progress.application.handler.DeleteBookProgressHandler;
import com.mrs.engagement_service.module.book_progress.application.handler.UpdateBookProgressHandler;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/book-progress")
@Tag(
        name = "Book Progress",
        description = "Endpoints for managing book progress"
)
public class BookProgressController {

    private final DefineBookStatusHandler defineBookStatusHandler;
    private final UpdateBookProgressHandler updateBookProgressHandler;
    private final DeleteBookProgressHandler deleteBookProgressHandler;

    private final AuthenticationService authenticationService;

    public BookProgressController(DefineBookStatusHandler defineBookStatusHandler, UpdateBookProgressHandler updateBookProgressHandler, DeleteBookProgressHandler deleteBookProgressHandler, AuthenticationService authenticationService) {
        this.defineBookStatusHandler = defineBookStatusHandler;
        this.updateBookProgressHandler = updateBookProgressHandler;
        this.deleteBookProgressHandler = deleteBookProgressHandler;
        this.authenticationService = authenticationService;
    }


    @PostMapping("/{bookId}/status")
    public ResponseEntity<BookProgressResponse> defineBookStatus(
            @PathVariable(value = "bookId") UUID bookId,
            @Valid BookStatusRequest request
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
    public ResponseEntity<BookProgressResponse> updateBookProgress(
            @PathVariable(value = "bookId") UUID bookId,
            int newCurrentPage
    ) {
        UUID userId = authenticationService.getAuthenticatedUserId();

        var response = updateBookProgressHandler.handle(
                newCurrentPage,
                bookId,
                userId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @DeleteMapping("/{bookId}")
    public ResponseEntity<Void> deleteBookProgress(
            @PathVariable(value = "bookId") UUID bookId
    ) {
        UUID userId = authenticationService.getAuthenticatedUserId();

        deleteBookProgressHandler.handle(
                bookId,
                userId
        );

        return ResponseEntity.ok().build();
    }



}
