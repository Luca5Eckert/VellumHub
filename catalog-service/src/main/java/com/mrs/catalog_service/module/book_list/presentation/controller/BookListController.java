package com.mrs.catalog_service.module.book_list.presentation.controller;

import com.mrs.catalog_service.module.book_list.application.command.CreateBookListCommand;
import com.mrs.catalog_service.module.book_list.application.command.DeleteBookListCommand;
import com.mrs.catalog_service.module.book_list.application.command.UpdateBookListCommand;
import com.mrs.catalog_service.module.book_list.application.query.GetAllBookListQuery;
import com.mrs.catalog_service.module.book_list.application.query.GetBookListByIdQuery;
import com.mrs.catalog_service.module.book_list.application.use_case.*;
import com.mrs.catalog_service.module.book_list.presentation.dto.response.BookListResponse;
import com.mrs.catalog_service.module.book_list.presentation.dto.request.CreatedBookListRequest;
import com.mrs.catalog_service.module.book_list.presentation.dto.request.UpdateBookListRequest;
import com.mrs.catalog_service.module.book_list.presentation.mapper.BookListMapper;
import com.mrs.catalog_service.share.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/book/list")
public class BookListController {

    private final AuthenticationService authenticationService;
    private final BookListMapper bookListMapper;

    private final CreateBookListUseCase createBookListUseCase;
    private final UpdateBookListUseCase updateBookListUseCase;
    private final DeleteBookListUseCase deleteBookListUseCase;
    private final GetBookListByIdUseCase getBookListByIdUseCase;
    private final GetAllBookListUseCase getAllBookListUseCase;

    public BookListController(AuthenticationService authenticationService, BookListMapper bookListMapper, CreateBookListUseCase createBookListUseCase, UpdateBookListUseCase updateBookListUseCase, DeleteBookListUseCase deleteBookListUseCase, GetBookListByIdUseCase getBookListByIdUseCase, GetAllBookListUseCase getAllBookListUseCase) {
        this.authenticationService = authenticationService;
        this.bookListMapper = bookListMapper;
        this.createBookListUseCase = createBookListUseCase;
        this.updateBookListUseCase = updateBookListUseCase;
        this.deleteBookListUseCase = deleteBookListUseCase;
        this.getBookListByIdUseCase = getBookListByIdUseCase;
        this.getAllBookListUseCase = getAllBookListUseCase;
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

    @DeleteMapping("/{bookListId}")
    @Operation(
            summary = "Delete a book list",
            description = "Delete an existing book list. Only the owner of the book list can perform this operation."
    )
    public ResponseEntity<Void> delete(
            @PathVariable(name = "bookListId") UUID bookListId
    ) {
        var userId = authenticationService.getAuthenticatedUserId();

        var command = DeleteBookListCommand.of(userId, bookListId);
        deleteBookListUseCase.execute(command);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{bookListId}")
    @Operation(
            summary = "Get the book by id",
            description = "Get the book list by id provide by user"
    )
    public ResponseEntity<BookListResponse> getById(
            @PathVariable(name = "bookListId") UUID bookListId
    ) {
        var userId = authenticationService.getAuthenticatedUserId();

        var query = GetBookListByIdQuery.of(userId, bookListId);
        var bookList = getBookListByIdUseCase.execute(query);

        return ResponseEntity
                .ok(bookListMapper.toResponse(bookList));
    }


}
