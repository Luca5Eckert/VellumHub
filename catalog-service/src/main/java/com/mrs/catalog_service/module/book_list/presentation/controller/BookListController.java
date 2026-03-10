package com.mrs.catalog_service.module.book_list.presentation.controller;

import com.mrs.catalog_service.module.book_list.application.command.CreateBookListCommand;
import com.mrs.catalog_service.module.book_list.application.use_case.CreateBookListUseCase;
import com.mrs.catalog_service.module.book_list.presentation.dto.BookListResponse;
import com.mrs.catalog_service.module.book_list.presentation.dto.CreatedBookListRequest;
import com.mrs.catalog_service.module.book_list.presentation.mapper.BookListMapper;
import com.mrs.catalog_service.share.service.AuthenticationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/book/list")
public class BookListController {

    private final AuthenticationService authenticationService;
    private final BookListMapper bookListMapper;

    private final CreateBookListUseCase createBookListUseCase;

    public BookListController(AuthenticationService authenticationService, BookListMapper bookListMapper, CreateBookListUseCase createBookListUseCase) {
        this.authenticationService = authenticationService;
        this.bookListMapper = bookListMapper;
        this.createBookListUseCase = createBookListUseCase;
    }

    @PostMapping
    public ResponseEntity<BookListResponse> create(
            @RequestBody @Valid CreatedBookListRequest request
    ) {
        var userId = authenticationService.getAuthenticatedUserId();

        var command = CreateBookListCommand.of(request.booksId(), userId);

        var bookList = createBookListUseCase.execute(command);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(bookListMapper.toResponse(bookList));
    }

}
