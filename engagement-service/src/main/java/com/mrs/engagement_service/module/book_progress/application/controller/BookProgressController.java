package com.mrs.engagement_service.module.book_progress.application.controller;

import com.mrs.engagement_service.module.book_progress.application.dto.BookProgressResponse;
import com.mrs.engagement_service.module.book_progress.application.dto.BookStatusRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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


    @PostMapping("/{bookId}/status")
    public ResponseEntity<BookProgressResponse> defineBookStatus(
            @PathVariable(value="bookId") UUID bookId,
            @Valid BookStatusRequest request
    ) {
        return ResponseEntity.ok(null);
    }

    @PutMapping("/{bookId}/progress")
    public ResponseEntity<BookProgressResponse> updateBookProgress(
            @PathVariable(value="bookId") UUID bookId,
            int newCurrentPage
    ){
        return ResponseEntity.ok(null);
    }

    @DeleteMapping("/{bookId]")
    public ResponseEntity<Void> deleteBookProgress(
            @PathVariable(value="bookId") UUID bookId
    ) {
        return ResponseEntity.ok().build();
    }




}
