package com.vellumhub.catalog_service.module.book_progress.application.handler;

import com.vellumhub.catalog_service.module.book_progress.application.dto.BookStatusRequest;
import com.vellumhub.catalog_service.module.book_progress.domain.event.UpdateBookProgressEvent;
import com.vellumhub.catalog_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressEventProducer;
import com.vellumhub.catalog_service.module.book_progress.domain.use_case.DefineBookStatusUseCase;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DefineBookStatusHandler {

    private final DefineBookStatusUseCase defineBookStatusUseCase;
    private final BookProgressEventProducer<String, UpdateBookProgressEvent> bookProgressEventProducer;


    public DefineBookStatusHandler(DefineBookStatusUseCase defineBookStatusUseCase, BookProgressEventProducer<String, UpdateBookProgressEvent> bookProgressEventProducer) {
        this.defineBookStatusUseCase = defineBookStatusUseCase;
        this.bookProgressEventProducer = bookProgressEventProducer;
    }

    @Transactional
    public void handle(BookStatusRequest bookStatusRequest, UUID userId, UUID bookId) {
        DefineBookStatusCommand defineBookStatusCommand = new DefineBookStatusCommand(
                userId,
                bookId,
                bookStatusRequest.status(),
                bookStatusRequest.currentPage()
        );

        UpdateBookProgressEvent event = defineBookStatusUseCase.execute(defineBookStatusCommand);

        bookProgressEventProducer.send("updated-progress", event.userId().toString(), event);
    }

}
