package com.vellumhub.catalog_service.module.book_progress.application.handler;

import com.vellumhub.catalog_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressDomainException;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressNotFoundException;
import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingProgressChange;
import com.vellumhub.catalog_service.module.book_progress.domain.port.ReadingProgressWriteGate;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Predicate;
import java.util.UUID;
import java.util.stream.IntStream;

@Component
public class BookReadingsHandler {
    private final DefineBookStatusHandler defineStatus;
    private final BookProgressRepository repository;

    private final UpdateBookProgressHandler progressEvents;
    private final ReadingProgressWriteGate gate;

    public BookReadingsHandler(DefineBookStatusHandler defineStatus, BookProgressRepository repository,
                               UpdateBookProgressHandler progressEvents, ReadingProgressWriteGate gate) {
        this.progressEvents = progressEvents;
        this.gate = gate;
        this.defineStatus = defineStatus;
        this.repository = repository;
    }

    @Transactional
    public Reading start(UUID bookId, UUID userId) {
        return start(bookId, userId, null);
    }

    @Transactional
    public Reading start(UUID bookId, UUID userId, OffsetDateTime startedAt) {
        if (startedAt != null && startedAt.isAfter(now())) {
            throw new BookProgressDomainException("Start time cannot be in the future");
        }
        UUID readingId = defineStatus.create(new DefineBookStatusCommand(
                userId, bookId, ReadingStatus.READING, 0, startedAt, null));
        return response(bookId, userId, readingId);
    }

    @Transactional
    public Reading progress(UUID bookId, UUID userId, UUID readingId, Integer page, Integer percentage,
                            OffsetDateTime readAt) {
        var now = now();
        return change(bookId, userId, readingId, now, reading -> reading.recordProgress(page, percentage,
                readAt == null ? null : readAt.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS), now), true, false);
    }

    @Transactional
    public Reading transition(UUID bookId, UUID userId, UUID readingId, ReadingStatus status) {
        var now = now();
        return change(bookId, userId, readingId, now, reading -> reading.transitionTo(status, now),
                status == ReadingStatus.COMPLETED, false);
    }

    @Transactional
    public Reading target(UUID bookId, UUID userId, UUID readingId, LocalDate target) {
        var now = now();
        return change(bookId, userId, readingId, now, reading -> reading.changeTarget(target, now.toLocalDate()),
                false, true);
    }

    private Reading change(UUID bookId, UUID userId, UUID readingId, OffsetDateTime now,
                           Predicate<BookProgress> update, boolean recordsPages, boolean targetOnly) {
        gate.requireWritable();
        var progress = repository.findOwnedReading(userId, bookId, readingId)
                .orElseThrow(BookProgressNotFoundException::new);
        int oldPage = progress.getCurrentPage();
        if (update.test(progress)) {
            var persisted = repository.save(progress);
            var readAt = recordsPages ? persisted.getLastReadAt().toInstant() : null;
            progressEvents.append(new ReadingProgressChange(
                    UUID.randomUUID(), now.toInstant(), readingId, userId, bookId,
                    persisted.getReadingStatus().name(), oldPage, persisted.getCurrentPage(),
                    persisted.getVersion(), ReadingProgressChange.Kind.UPDATED, readAt, persisted.getTargetEndDate(), targetOnly, false));
        }
        return response(bookId, userId, readingId);
    }

    @Transactional
    public void delete(UUID bookId, UUID userId, UUID readingId) {
        gate.requireWritable();
        var reading = repository.findOwnedReading(userId, bookId, readingId)
                .orElseThrow(BookProgressNotFoundException::new);
        deleteAndPublish(reading, bookId, userId);
    }

    @Transactional
    public void deleteAll(UUID bookId, UUID userId) {
        gate.requireWritable();
        var readings = repository.findHistory(userId, bookId);
        if (readings.isEmpty()) {
            throw new BookProgressNotFoundException();
        }
        readings.forEach(reading -> deleteAndPublish(reading, bookId, userId));
    }

    private void deleteAndPublish(BookProgress reading, UUID bookId, UUID userId) {
        var fact = new ReadingProgressChange(UUID.randomUUID(), now().toInstant(), reading.getId(),
                userId, bookId, reading.getReadingStatus().name(), reading.getCurrentPage(), reading.getCurrentPage(),
                reading.getVersion() + 1, ReadingProgressChange.Kind.UPDATED, null, null, false, true);
        repository.delete(reading);
        progressEvents.append(fact);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private Reading response(UUID bookId, UUID userId, UUID readingId) {
        return list(bookId, userId).stream()
                .filter(reading -> reading.progress().getId().equals(readingId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public int pageCount(UUID readingId, UUID userId) {
        return repository.findByIdAndUserId(readingId, userId)
                .orElseThrow(BookProgressNotFoundException::new)
                .getBook().getPageCount();
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
