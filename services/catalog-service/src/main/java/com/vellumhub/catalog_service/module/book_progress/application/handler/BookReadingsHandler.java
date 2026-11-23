package com.vellumhub.catalog_service.module.book_progress.application.handler;

import com.vellumhub.catalog_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Component
public class BookReadingsHandler {
    private final DefineBookStatusHandler defineStatus;
    private final BookProgressRepository repository;

    public BookReadingsHandler(DefineBookStatusHandler defineStatus, BookProgressRepository repository) {
        this.defineStatus = defineStatus;
        this.repository = repository;
    }

    @Transactional
    public Reading start(UUID bookId, UUID userId) {
        UUID readingId = defineStatus.create(new DefineBookStatusCommand(
                userId, bookId, ReadingStatus.READING, 0, null, null));
        return list(bookId, userId).stream()
                .filter(reading -> reading.progress().getId().equals(readingId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Reading> list(UUID bookId, UUID userId) {
        var history = repository.findHistory(userId, bookId);
        return IntStream.range(0, history.size())
                .mapToObj(index -> new Reading(history.get(index), history.size() - index))
                .toList();
    }

    public record Reading(BookProgress progress, int readingNumber) {}
}
