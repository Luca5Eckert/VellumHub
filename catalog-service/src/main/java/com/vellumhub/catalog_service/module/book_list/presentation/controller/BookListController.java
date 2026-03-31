package com.vellumhub.catalog_service.module.book_list.presentation.controller;

import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book_list.application.command.list.AddBookInListCommand;
import com.vellumhub.catalog_service.module.book_list.application.command.list.CreateBookListCommand;
import com.vellumhub.catalog_service.module.book_list.application.command.list.DeleteBookListCommand;
import com.vellumhub.catalog_service.module.book_list.application.command.list.UpdateBookListCommand;
import com.vellumhub.catalog_service.module.book_list.application.query.list.GetAllBookListQuery;
import com.vellumhub.catalog_service.module.book_list.application.query.list.GetBookListByIdQuery;
import com.vellumhub.catalog_service.module.book_list.application.use_case.list.*;
import com.vellumhub.catalog_service.module.book_list.domain.model.TypeBookList;
import com.vellumhub.catalog_service.module.book_list.presentation.dto.response.list.BookListGetAllResponse;
import com.vellumhub.catalog_service.module.book_list.presentation.dto.response.list.BookListResponse;
import com.vellumhub.catalog_service.module.book_list.presentation.dto.request.list.CreatedBookListRequest;
import com.vellumhub.catalog_service.module.book_list.presentation.dto.request.list.UpdateBookListRequest;
import com.vellumhub.catalog_service.module.book_list.presentation.mapper.BookListMapper;
import com.vellumhub.catalog_service.share.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/book-list")
public class BookListController {

    private final AuthenticationService authenticationService;
    private final BookListMapper bookListMapper;

    private final CreateBookListUseCase createBookListUseCase;
    private final UpdateBookListUseCase updateBookListUseCase;
    private final DeleteBookListUseCase deleteBookListUseCase;
    private final GetBookListByIdUseCase getBookListByIdUseCase;
    private final GetAllBookListUseCase getAllBookListUseCase;
    private final AddBookInListUseCase addBookInListUseCase;

    public BookListController(AuthenticationService authenticationService, BookListMapper bookListMapper, CreateBookListUseCase createBookListUseCase, UpdateBookListUseCase updateBookListUseCase, DeleteBookListUseCase deleteBookListUseCase, GetBookListByIdUseCase getBookListByIdUseCase, GetAllBookListUseCase getAllBookListUseCase, AddBookInListUseCase addBookInListUseCase) {
        this.authenticationService = authenticationService;
        this.bookListMapper = bookListMapper;
        this.createBookListUseCase = createBookListUseCase;
        this.updateBookListUseCase = updateBookListUseCase;
        this.deleteBookListUseCase = deleteBookListUseCase;
        this.getBookListByIdUseCase = getBookListByIdUseCase;
        this.getAllBookListUseCase = getAllBookListUseCase;
        this.addBookInListUseCase = addBookInListUseCase;
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
            summary = "Retrieve a specific book list",
            description = "Fetches the details of a book list using the unique identifier provided by the user."
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

    @GetMapping
    @Operation(
            summary = "Get all books by filters",
            description = "Get the existing book lists who matched with filters"
    )
    public ResponseEntity<List<BookListGetAllResponse>> getAll(
            @RequestParam(name = "Title", required = false) String title,
            @RequestParam(name = "Description", required = false) String description,
            @RequestParam(name = "Owner's id of book list", required = false) UUID userOwnerList,
            @RequestParam(name = "Genres of books in list", required = false) Set<Genre> genres,
            @RequestParam(name = "Books id", required = false) Set<UUID> booksId,
            @RequestParam(name = "Type of book list", required = false) TypeBookList typeBookList,
            @RequestParam(name = "Number of page", required = false) int numberPage,
            @RequestParam(name = "Size of page", required = false) int sizePage
    ) {
        var userId = authenticationService.getAuthenticatedUserId();


        var query = GetAllBookListQuery.of(
                title,
                description,
                userOwnerList,
                genres,
                booksId,
                typeBookList,
                userId,
                numberPage,
                sizePage
        );

        var bookLists = getAllBookListUseCase.execute(query);

        var response = bookLists.stream()
                .map(bookListMapper::toGetAllResponse)
                .toList();

        return ResponseEntity
                .ok(response);
    }

    @PostMapping("/{bookListId}/books/{bookId}")
    @Operation(
            summary = "Add a book to a book list",
            description = "Add a book to an existing book list."
    )
    public ResponseEntity<BookListResponse> addBookToList(
            @PathVariable(name = "bookListId") UUID bookListId,
            @PathVariable(name = "bookId") UUID bookId
    ) {
        var userId = authenticationService.getAuthenticatedUserId();

        var command = AddBookInListCommand.of(userId, bookListId, bookId);
        addBookInListUseCase.execute(command);

        return ResponseEntity.ok().build();
    }

}
