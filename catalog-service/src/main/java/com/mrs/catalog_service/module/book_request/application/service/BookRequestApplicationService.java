package com.mrs.catalog_service.module.book_request.application.service;

import com.mrs.catalog_service.module.book_request.application.dto.BookRequestResponse;
import com.mrs.catalog_service.module.book_request.application.dto.CreateBookRequestDto;
import com.mrs.catalog_service.module.book_request.application.mapper.BookRequestMapper;
import com.mrs.catalog_service.module.book_request.domain.BookRequest;
import com.mrs.catalog_service.module.book_request.domain.command.CreateBookRequestCommand;
import com.mrs.catalog_service.module.book_request.domain.use_case.CreateBookRequestUseCase;
import org.springframework.stereotype.Service;

@Service
public class BookRequestApplicationService {

    private final CreateBookRequestUseCase createBookRequestUseCase;

    private final BookRequestMapper bookRequestMapper;

    public BookRequestApplicationService(CreateBookRequestUseCase createBookRequestUseCase, BookRequestMapper bookRequestMapper) {
        this.createBookRequestUseCase = createBookRequestUseCase;
        this.bookRequestMapper = bookRequestMapper;
    }

    public BookRequestResponse create(CreateBookRequestDto createBookRequestDto) {
        CreateBookRequestCommand command = bookRequestMapper.toCreateBookRequestCommand(createBookRequestDto);

        BookRequest bookRequest = createBookRequestUseCase.execute(command);

        return bookRequestMapper.toBookRequestResponse(bookRequest);
    }

}
