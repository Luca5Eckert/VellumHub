package com.vellumhub.catalog_service.module.book_progress.application.handler;
import com.vellumhub.catalog_service.module.book_progress.application.dto.BookStatusRequest;
import com.vellumhub.catalog_service.module.book_progress.application.event.ReadingProgressEventFactory;
import com.vellumhub.catalog_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.use_case.DefineBookStatusUseCase;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.application.port.OutboxWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Component
public class DefineBookStatusHandler {
    private final DefineBookStatusUseCase useCase;
    private final OutboxWriter outbox;
    private final ReadingProgressEventFactory events;
    public DefineBookStatusHandler(DefineBookStatusUseCase useCase, OutboxWriter outbox,
                                   ReadingProgressEventFactory events) {
        this.useCase = useCase; this.outbox = outbox; this.events = events;
    }
    @Transactional
    public void handle(BookStatusRequest request, UUID userId, UUID bookId) {
        var event = useCase.execute(new DefineBookStatusCommand(
                userId, bookId, request.status(), request.currentPage(), request.startedAt(), request.endAt()));
        outbox.append(OutboxMessage.readingProgress(events.map(event)));
    }
}


