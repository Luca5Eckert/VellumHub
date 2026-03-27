package com.mrs.recommendation_service.module.user_profile.application.handler;

import com.mrs.recommendation_service.module.user_profile.application.event.CreatedRatingEvent;
import com.mrs.recommendation_service.module.user_profile.domain.command.UpdateUserProfileWithRatingCommand;
import com.mrs.recommendation_service.module.user_profile.domain.use_case.UpdateUserProfileWithRatingUseCase;
import org.springframework.stereotype.Component;

@Component
public class CreatedRatingConsumerHandler {

    private final UpdateUserProfileWithRatingUseCase updateUserProfileWithRatingUseCase;

    public CreatedRatingConsumerHandler(UpdateUserProfileWithRatingUseCase updateUserProfileWithRatingUseCase) {
        this.updateUserProfileWithRatingUseCase = updateUserProfileWithRatingUseCase;
    }

    public void handle(CreatedRatingEvent event){
        UpdateUserProfileWithRatingCommand command = new UpdateUserProfileWithRatingCommand(
                event.userId(),
                event.bookId(),
                0,
                event.stars(),
                false
        );

        updateUserProfileWithRatingUseCase.execute(command);
    }

}
