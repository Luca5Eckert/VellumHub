package com.vellumhub.catalog_service.module.book_progress.application.handler;
import com.vellumhub.catalog_service.module.book_progress.application.event.ReadingProgressEventFactory;
import com.vellumhub.catalog_service.module.book_progress.domain.command.UpdateBookProgressCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.use_case.UpdateBookProgressUseCase;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.application.port.OutboxWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Component
public class UpdateBookProgressHandler {
    private final UpdateBookProgressUseCase useCase;
    private final OutboxWriter outbox;
    private final ReadingProgressEventFactory events;
    public UpdateBookProgressHandler(UpdateBookProgressUseCase useCase, OutboxWriter outbox,
                                     ReadingProgressEventFactory events) {
        this.useCase = useCase; this.outbox = outbox; this.events = events;
    }
    @Transactional
    public void handle(int currentPage, UUID bookId, UUID userId) {
        var event = useCase.execute(new UpdateBookProgressCommand(userId, bookId, currentPage));
        outbox.append(OutboxMessage.readingProgress(events.updated(event)));
    }
}

