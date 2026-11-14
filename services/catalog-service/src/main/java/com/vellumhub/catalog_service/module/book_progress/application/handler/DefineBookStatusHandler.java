package com.vellumhub.catalog_service.module.book_progress.application.handler;
import com.vellumhub.catalog_service.module.book_progress.application.dto.BookStatusRequest;
import com.vellumhub.catalog_service.module.book_progress.application.event.ReadingProgressEventFactory;
import com.vellumhub.catalog_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.use_case.DefineBookStatusUseCase;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.application.port.OutboxWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.vellumhub.catalog_service.module.book_progress.domain.port.ReadingProgressWriteGate;
import java.util.UUID;
@Component
public class DefineBookStatusHandler {
    private final DefineBookStatusUseCase useCase;
    private final OutboxWriter outbox;
    private final ReadingProgressEventFactory events;
    private final ReadingProgressWriteGate writeGate;
    private final boolean avro;
    @Autowired
    public DefineBookStatusHandler(DefineBookStatusUseCase useCase, OutboxWriter outbox,
                                   ReadingProgressEventFactory events, ReadingProgressWriteGate writeGate,
                                   @Value("${app.reading-progress.events.wire-format:json}") String wireFormat) {
        this.useCase = useCase;
        this.outbox = outbox;
        this.events = events;
        this.writeGate = writeGate;
        if (!"json".equalsIgnoreCase(wireFormat) && !"avro".equalsIgnoreCase(wireFormat))
            throw new IllegalArgumentException("Unsupported Reading Progress wire format");
        this.avro = "avro".equalsIgnoreCase(wireFormat);
    }
    /** Retained for source-compatible unit tests without the DB gate. */
    public DefineBookStatusHandler(DefineBookStatusUseCase useCase, OutboxWriter outbox,
                                   ReadingProgressEventFactory events) {
        this(useCase, outbox, events, () -> {}, "json");
    }
    @Transactional
    public void handle(BookStatusRequest request, UUID userId, UUID bookId) {
        writeGate.requireWritable();
        var event = useCase.execute(new DefineBookStatusCommand(
                userId, bookId, request.status(), request.currentPage(), request.startedAt(), request.endAt()));
        outbox.append(OutboxMessage.readingProgress(events.map(event), avro));
    }
}


