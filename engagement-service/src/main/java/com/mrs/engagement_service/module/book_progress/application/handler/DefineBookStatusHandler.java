package com.mrs.engagement_service.module.book_progress.application.handler;

import com.mrs.engagement_service.module.book_progress.application.dto.BookProgressResponse;
import com.mrs.engagement_service.module.book_progress.application.dto.BookStatusRequest;
import com.mrs.engagement_service.module.book_progress.application.mapper.BookProgressMapper;
import com.mrs.engagement_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.mrs.engagement_service.module.book_progress.domain.use_case.DefineBookStatusUseCase;
import com.mrs.engagement_service.module.rating.domain.model.BookProgress;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DefineBookStatusHandler {

    private final DefineBookStatusUseCase defineBookStatusUseCase;

    private final BookProgressMapper bookProgressMapper;

    public DefineBookStatusHandler(DefineBookStatusUseCase defineBookStatusUseCase, BookProgressMapper bookProgressMapper) {
        this.defineBookStatusUseCase = defineBookStatusUseCase;
        this.bookProgressMapper = bookProgressMapper;
    }

    @Transactional
    public BookProgressResponse handle(BookStatusRequest bookStatusRequest, UUID userId, UUID bookId) {
        DefineBookStatusCommand defineBookStatusCommand = new DefineBookStatusCommand(
                bookId,
                userId,
                bookStatusRequest.status(),
                bookStatusRequest.currentPage()
        );

        BookProgress bookProgress = defineBookStatusUseCase.execute(defineBookStatusCommand);

        return bookProgressMapper.toResponse(bookProgress);
    }
}
