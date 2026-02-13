package com.mrs.catalog_service.module.book_progress.application.handler;

import com.mrs.catalog_service.module.book_progress.application.dto.BookProgressResponse;
import com.mrs.catalog_service.module.book_progress.application.mapper.BookProgressMapper;
import com.mrs.catalog_service.module.book_progress.domain.command.UpdateBookProgressCommand;
import com.mrs.catalog_service.module.book_progress.domain.use_case.UpdateBookProgressUseCase;
import com.mrs.catalog_service.module.book_progress.domain.model.BookProgress;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class UpdateBookProgressHandler {

    private final UpdateBookProgressUseCase updateBookProgressUseCase;

    private final BookProgressMapper mapper;

    public UpdateBookProgressHandler(UpdateBookProgressUseCase updateBookProgressUseCase, BookProgressMapper mapper) {
        this.updateBookProgressUseCase = updateBookProgressUseCase;
        this.mapper = mapper;
    }

    @Transactional
    public BookProgressResponse handle(int currentPage, UUID bookId, UUID userId){
        UpdateBookProgressCommand updateBookProgressCommand = new UpdateBookProgressCommand(
                userId,
                bookId,
                currentPage
        );

        BookProgress bookProgress = updateBookProgressUseCase.execute(updateBookProgressCommand);

        return mapper.toResponse(bookProgress);
    }

}
