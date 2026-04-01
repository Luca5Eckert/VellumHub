package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.reaction.application.command.CreateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.domain.event.ReactionEvent;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.port.EventProducer;
import com.vellumhub.engagement_service.module.reaction.domain.port.ReactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateReactionUseCase {

    private final ReactionRepository reactionRepository;
    private final BookSnapshotRepository bookSnapshotRepository;
    private final EventProducer<String, ReactionEvent> eventProducer;

    public CreateReactionUseCase(ReactionRepository reactionRepository, BookSnapshotRepository bookSnapshotRepository, EventProducer<String, ReactionEvent> eventProducer) {
        this.reactionRepository = reactionRepository;
        this.bookSnapshotRepository = bookSnapshotRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public void execute(CreateReactionCommand command) {
        BookSnapshot book = bookSnapshotRepository.findById(command.bookId())
                .orElseThrow(() -> new RuntimeException("Book snapshot not found"));

        var reaction = Reaction.of(
                command.userId(),
                book,
                command.typeReaction()
        );

        reactionRepository.save(reaction);

        var event = ReactionEvent.from(reaction);

        eventProducer.send("user-reacted", event.userId().toString(), event);
    }

}