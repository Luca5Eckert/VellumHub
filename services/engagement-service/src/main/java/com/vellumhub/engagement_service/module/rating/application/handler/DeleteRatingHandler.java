package com.vellumhub.engagement_service.module.rating.application.handler;

import com.vellumhub.engagement_service.module.rating.domain.command.DeleteRatingCommand;
import com.vellumhub.engagement_service.module.rating.domain.use_case.DeleteRatingUseCase;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DeleteRatingHandler {

    private final DeleteRatingUseCase useCase;

    public DeleteRatingHandler(DeleteRatingUseCase useCase) {
        this.useCase = useCase;
    }

    public void handle(long ratingId, UUID userId) {
        var command = new DeleteRatingCommand(
                ratingId,
                userId
        );

        useCase.execute(command);
    }
}
