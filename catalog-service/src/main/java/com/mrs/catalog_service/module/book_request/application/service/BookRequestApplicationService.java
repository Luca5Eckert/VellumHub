package com.mrs.catalog_service.module.book_request.application.service;

import com.mrs.catalog_service.module.book_request.application.dto.BookRequestResponse;
import com.mrs.catalog_service.module.book_request.application.dto.CreateBookRequestDto;
import com.mrs.catalog_service.module.book_request.application.mapper.BookRequestMapper;
import com.mrs.catalog_service.module.book_request.domain.BookRequest;
import com.mrs.catalog_service.module.book_request.domain.command.CreateBookRequestCommand;
import com.mrs.catalog_service.module.book_request.domain.use_case.ApproveBookRequestUseCase;
import com.mrs.catalog_service.module.book_request.domain.use_case.CreateBookRequestUseCase;
import com.mrs.catalog_service.module.book_request.domain.use_case.GetAllBookRequestUseCase;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BookRequestApplicationService {

    private final CreateBookRequestUseCase createBookRequestUseCase;
    private final ApproveBookRequestUseCase approveBookRequestUseCase;
    private final GetAllBookRequestUseCase getAllBookRequestUseCase;

    private final BookRequestMapper bookRequestMapper;

    public BookRequestApplicationService(CreateBookRequestUseCase createBookRequestUseCase, ApproveBookRequestUseCase approveBookRequestUseCase, GetAllBookRequestUseCase getAllBookRequestUseCase, BookRequestMapper bookRequestMapper) {
        this.createBookRequestUseCase = createBookRequestUseCase;
        this.approveBookRequestUseCase = approveBookRequestUseCase;
        this.getAllBookRequestUseCase = getAllBookRequestUseCase;
        this.bookRequestMapper = bookRequestMapper;
    }

    public BookRequestResponse create(CreateBookRequestDto createBookRequestDto) {
        CreateBookRequestCommand command = bookRequestMapper.toCreateBookRequestCommand(createBookRequestDto);

        BookRequest bookRequest = createBookRequestUseCase.execute(command);

        return bookRequestMapper.toBookRequestResponse(bookRequest);
    }

    public void approve(Long requestId) {
        approveBookRequestUseCase.execute(requestId);
    }

    public List<BookRequestResponse> getAll(
            int page,
            int size
    ) {
        var bookRequests = getAllBookRequestUseCase.execute(page, size);

        return bookRequests.stream()
                .map(bookRequestMapper::toBookRequestResponse)
                .toList();
    }

}
