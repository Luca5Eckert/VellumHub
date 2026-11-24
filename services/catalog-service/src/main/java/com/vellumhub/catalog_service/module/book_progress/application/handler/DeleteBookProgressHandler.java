package com.vellumhub.catalog_service.module.book_progress.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DeleteBookProgressHandler {

    private final BookReadingsHandler readings;

    public DeleteBookProgressHandler(BookReadingsHandler readings) {
        this.readings = readings;
    }

    @Transactional
    public void handle(UUID bookId, UUID userId) {
        readings.deleteAll(bookId, userId);
    }

}
