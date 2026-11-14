package com.vellumhub.catalog_service.module.book_progress.application.handler;
import com.vellumhub.catalog_service.module.book_progress.application.event.ReadingProgressEventFactory;
import com.vellumhub.catalog_service.module.book_progress.domain.command.UpdateBookProgressCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.use_case.UpdateBookProgressUseCase;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.application.port.OutboxWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.vellumhub.catalog_service.module.book_progress.domain.port.ReadingProgressWriteGate;
import java.util.UUID;
@Component
public class UpdateBookProgressHandler {
    private final UpdateBookProgressUseCase useCase;
    private final OutboxWriter outbox;
    private final ReadingProgressEventFactory events;
    private final ReadingProgressWriteGate writeGate;
    private final boolean avro;
    @Autowired
    public UpdateBookProgressHandler(UpdateBookProgressUseCase useCase, OutboxWriter outbox,
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
    public UpdateBookProgressHandler(UpdateBookProgressUseCase useCase, OutboxWriter outbox,
                                     ReadingProgressEventFactory events) {
        this(useCase, outbox, events, () -> {}, "json");
    }
    @Transactional
    public void handle(int currentPage, UUID bookId, UUID userId) {
        writeGate.requireWritable();
        var event = useCase.execute(new UpdateBookProgressCommand(userId, bookId, currentPage));
        if (event != null) {
            outbox.append(OutboxMessage.readingProgress(events.map(event), avro));
        }
    }
}


