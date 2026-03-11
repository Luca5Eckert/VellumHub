package com.mrs.catalog_service.module.book_list.presentation.controller;

import com.mrs.catalog_service.module.book_list.application.command.CreateBookListCommand;
import com.mrs.catalog_service.module.book_list.application.command.UpdateBookListCommand;
import com.mrs.catalog_service.module.book_list.application.use_case.CreateBookListUseCase;
import com.mrs.catalog_service.module.book_list.application.use_case.UpdateBookListUseCase;
import com.mrs.catalog_service.module.book_list.presentation.dto.BookListResponse;
import com.mrs.catalog_service.module.book_list.presentation.dto.CreatedBookListRequest;
import com.mrs.catalog_service.module.book_list.presentation.dto.UpdateBookListRequest;
import com.mrs.catalog_service.module.book_list.presentation.mapper.BookListMapper;
import com.mrs.catalog_service.share.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/book/list")
public class BookListController {

    private final AuthenticationService authenticationService;
    private final BookListMapper bookListMapper;

    private final CreateBookListUseCase createBookListUseCase;
    private final UpdateBookListUseCase updateBookListUseCase;

    public BookListController(AuthenticationService authenticationService, BookListMapper bookListMapper, CreateBookListUseCase createBookListUseCase, UpdateBookListUseCase updateBookListUseCase) {
        this.authenticationService = authenticationService;
        this.bookListMapper = bookListMapper;
        this.createBookListUseCase = createBookListUseCase;
        this.updateBookListUseCase = updateBookListUseCase;
    }

    @Operation(summary = "Create a new book list")
    @PostMapping
    public ResponseEntity<BookListResponse> create(
            @RequestBody @Valid CreatedBookListRequest request
    ) {
        var userId = authenticationService.getAuthenticatedUserId();

        var command = CreateBookListCommand.of(
                request.title(),
                request.description(),
                request.type(),
                request.booksId(),
                userId
        );
        var bookList = createBookListUseCase.execute(command);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(bookListMapper.toResponse(bookList));
    }

    @PutMapping("/{bookListId}")
    @Operation(
            summary = "Update a book list",
            description = "Update the details of an existing book list. Only the owner of the book list can perform this operation."
    )
    public ResponseEntity<BookListResponse> update(
            @RequestBody @Valid UpdateBookListRequest request,
            @PathVariable(name = "bookListId") UUID bookListId
    ) {
        var userId = authenticationService.getAuthenticatedUserId();

        var command = UpdateBookListCommand.of(
                request.title(),
                request.description(),
                request.typeBookList(),
                bookListId,
                userId
        );
        var updatedBookList = updateBookListUseCase.execute(command);

        return ResponseEntity.ok(bookListMapper.toResponse(updatedBookList));
     }

}
