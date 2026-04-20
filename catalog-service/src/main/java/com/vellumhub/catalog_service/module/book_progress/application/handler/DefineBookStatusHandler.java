package com.vellumhub.catalog_service.module.book_progress.application.handler;

import com.vellumhub.catalog_service.module.book_progress.application.dto.BookStatusRequest;
import com.vellumhub.catalog_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.event.CreateBookProgressEvent;
import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressEventProducer;
import com.vellumhub.catalog_service.module.book_progress.domain.use_case.DefineBookStatusUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DefineBookStatusHandler {

    private static final String CREATE_READING_PROGRESS_TOPIC = "create-reading-progress";

    private final DefineBookStatusUseCase defineBookStatusUseCase;
    private final BookProgressEventProducer<String, CreateBookProgressEvent> bookProgressEventProducer;

    public DefineBookStatusHandler(
            DefineBookStatusUseCase defineBookStatusUseCase,
            BookProgressEventProducer<String, CreateBookProgressEvent> bookProgressEventProducer
    ) {
        this.defineBookStatusUseCase = defineBookStatusUseCase;
        this.bookProgressEventProducer = bookProgressEventProducer;
    }

    @Transactional
    public void handle(BookStatusRequest bookStatusRequest, UUID userId, UUID bookId) {
        var command = new DefineBookStatusCommand(
                userId,
                bookId,
                bookStatusRequest.status(),
                bookStatusRequest.currentPage(),
                bookStatusRequest.startedAt(),
                bookStatusRequest.endAt()
        );

        CreateBookProgressEvent event = defineBookStatusUseCase.execute(command);

        bookProgressEventProducer.send(
                CREATE_READING_PROGRESS_TOPIC,
                event.userId().toString(),
                event
        );
    }

}