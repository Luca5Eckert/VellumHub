package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.ReactionChangedCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.ReactionChangedUseCase;
import com.vellumhub.recommendation_service.module.user_profile.presentation.event.ReactionChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserReactionConsumerEvent {

    private final ReactionChangedUseCase reactionChangedUseCase;

    public UserReactionConsumerEvent(ReactionChangedUseCase reactionChangedUseCase) {
        this.reactionChangedUseCase = reactionChangedUseCase;
    }

    @KafkaListener(
            topics = "user-reaction-changed",
            groupId = "recommendation-service"
    )
    public void consume(ReactionChangedEvent event) {
        log.info("Event received: User reaction changed. UserId={}, BookId={}, Reaction={}",
                event.userId(),
                event.bookId(),
                event.typeReaction());

        var command = ReactionChangedCommand.of(
                event.userId(),
                event.bookId(),
                event.typeReaction()
        );

        reactionChangedUseCase.execute(command);

        log.info("User reaction change event processed successfully. UserId={}, BookId={}",
                event.userId(),
                event.bookId());
    }

}
