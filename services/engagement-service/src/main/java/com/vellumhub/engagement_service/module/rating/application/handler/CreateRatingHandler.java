package com.vellumhub.engagement_service.module.rating.application.handler;

import com.vellumhub.engagement_service.module.rating.application.dto.CreateRatingRequest;
import com.vellumhub.engagement_service.module.rating.application.event.RatingEventRecorder;
import com.vellumhub.engagement_service.module.rating.domain.command.CreateRatingCommand;
import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.use_case.CreateRatingUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class CreateRatingHandler {

    private final CreateRatingUseCase createRatingUseCase;
    private final RatingEventRecorder recorder;

    public CreateRatingHandler(CreateRatingUseCase createRatingUseCase, RatingEventRecorder recorder) {
        this.createRatingUseCase = createRatingUseCase;
        this.recorder = recorder;
    }

    @Transactional
    public void handle(CreateRatingRequest request, UUID userId){
        CreateRatingCommand command = new CreateRatingCommand(
                userId,
                request.bookId(),
                request.stars(),
                request.review()
        );

        Rating rating = createRatingUseCase.execute(command);


        recorder.created(rating);
    }

}

