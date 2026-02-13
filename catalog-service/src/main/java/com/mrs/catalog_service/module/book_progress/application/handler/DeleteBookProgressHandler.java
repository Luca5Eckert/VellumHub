package com.mrs.catalog_service.module.book_progress.application.handler;

import com.mrs.catalog_service.module.book_progress.domain.command.DeleteBookProgressCommand;
import com.mrs.catalog_service.module.book_progress.domain.use_case.DeleteBookProgressUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DeleteBookProgressHandler {

    private final DeleteBookProgressUseCase deleteBookProgressUseCase;

    public DeleteBookProgressHandler(DeleteBookProgressUseCase deleteBookProgressUseCase) {
        this.deleteBookProgressUseCase = deleteBookProgressUseCase;
    }

    @Transactional
    public void handle(UUID bookId, UUID userId) {
        DeleteBookProgressCommand command = new DeleteBookProgressCommand(
                bookId,
                userId
        );

        deleteBookProgressUseCase.execute(command);
    }

}
