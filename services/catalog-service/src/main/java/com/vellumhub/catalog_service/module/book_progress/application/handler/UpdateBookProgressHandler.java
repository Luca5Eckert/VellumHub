package com.vellumhub.catalog_service.module.book_progress.application.handler;

import com.vellumhub.catalog_service.module.book_progress.application.dto.BookProgressResponse;
import com.vellumhub.catalog_service.module.book_progress.application.mapper.BookProgressMapper;
import com.vellumhub.catalog_service.module.book_progress.domain.command.UpdateBookProgressCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressEventProducer;
import com.vellumhub.catalog_service.module.book_progress.domain.use_case.UpdateBookProgressUseCase;
import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.readingprogress.UpdateBookProgressEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class UpdateBookProgressHandler {

    private final UpdateBookProgressUseCase updateBookProgressUseCase;
    private final BookProgressEventProducer<String, UpdateBookProgressEvent> bookProgressEventProducer;

    public UpdateBookProgressHandler(UpdateBookProgressUseCase updateBookProgressUseCase, BookProgressEventProducer<String, UpdateBookProgressEvent> bookProgressEventProducer) {
        this.updateBookProgressUseCase = updateBookProgressUseCase;
        this.bookProgressEventProducer = bookProgressEventProducer;
    }
    @Transactional
    public void handle(int currentPage, UUID bookId, UUID userId){
        UpdateBookProgressCommand updateBookProgressCommand = new UpdateBookProgressCommand(
                userId,
                bookId,
                currentPage
        );

        var event = updateBookProgressUseCase.execute(updateBookProgressCommand);

        bookProgressEventProducer.send(KafkaTopics.UPDATED_READING_PROGRESS, event.userId().toString(), event);
    }

}
