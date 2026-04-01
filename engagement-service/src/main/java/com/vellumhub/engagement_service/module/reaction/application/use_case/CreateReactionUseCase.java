package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.reaction.application.command.CreateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.port.ReactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateReactionUseCase {

    private final ReactionRepository interactionRepository;
    private final BookSnapshotRepository bookSnapshotRepository;

    public CreateReactionUseCase(ReactionRepository interactionRepository, BookSnapshotRepository bookSnapshotRepository) {
        this.interactionRepository = interactionRepository;
        this.bookSnapshotRepository = bookSnapshotRepository;
    }

    @Transactional
    public void execute(CreateReactionCommand command){
        BookSnapshot book = bookSnapshotRepository.findById(command.bookId())
                .orElseThrow(() -> new RuntimeException("Book not found"));

        var interaction = Reaction.of(
                command.userId(),
                book,
                command.typeReaction()
        );

        interactionRepository.save(interaction);
    }


}
