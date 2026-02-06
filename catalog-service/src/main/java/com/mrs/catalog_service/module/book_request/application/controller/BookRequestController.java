package com.mrs.catalog_service.module.book_request.application.controller;

import com.mrs.catalog_service.module.book_request.application.dto.BookRequestResponse;
import com.mrs.catalog_service.module.book_request.application.dto.CreateBookRequestDto;
import com.mrs.catalog_service.module.book_request.application.service.BookRequestApplicationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/book-requests")
@Tag(name = "Book Request", description = "Endpoints for managing book requests")
public class BookRequestController {

    private final BookRequestApplicationService bookRequestApplicationService;

    public BookRequestController(BookRequestApplicationService bookRequestApplicationService) {
        this.bookRequestApplicationService = bookRequestApplicationService;
    }

    @PostMapping
    public ResponseEntity<BookRequestResponse> create(CreateBookRequestDto request) {
        BookRequestResponse response = bookRequestApplicationService.create(request);
        return ResponseEntity.ok(response);
    }

}
