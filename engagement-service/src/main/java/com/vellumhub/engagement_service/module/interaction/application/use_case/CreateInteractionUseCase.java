package com.vellumhub.engagement_service.module.interaction.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.interaction.application.command.CreateInteractionCommand;
import com.vellumhub.engagement_service.module.interaction.domain.model.Interaction;
import com.vellumhub.engagement_service.module.interaction.domain.port.InteractionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateInteractionUseCase {

    private final InteractionRepository interactionRepository;
    private final BookSnapshotRepository bookSnapshotRepository;

    public CreateInteractionUseCase(InteractionRepository interactionRepository, BookSnapshotRepository bookSnapshotRepository) {
        this.interactionRepository = interactionRepository;
        this.bookSnapshotRepository = bookSnapshotRepository;
    }

    @Transactional
    public void execute(CreateInteractionCommand command){
        BookSnapshot book = bookSnapshotRepository.findById(command.bookId())
                .orElseThrow(() -> new RuntimeException("Book not found"));

        var interaction = Interaction.of(
                command.userId(),
                book,
                command.typeInteraction()
        );

        interactionRepository.save(interaction);
    }


}
