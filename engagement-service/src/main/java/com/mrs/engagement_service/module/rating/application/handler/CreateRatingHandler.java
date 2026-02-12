package com.mrs.engagement_service.module.rating.application.handler;

import com.mrs.engagement_service.module.rating.application.dto.CreateRatingRequest;
import com.mrs.engagement_service.module.rating.domain.command.CreateRatingCommand;
import com.mrs.engagement_service.module.rating.domain.use_case.CreateRatingUseCase;
import org.springframework.stereotype.Component;

@Component
public class CreateRatingHandler {

    private final CreateRatingUseCase createRatingUseCase;

    public CreateRatingHandler(CreateRatingUseCase createRatingUseCase) {
        this.createRatingUseCase = createRatingUseCase;
    }

    public void handle(CreateRatingRequest request){
        CreateRatingCommand command = new CreateRatingCommand(
                request.userId(),
                request.bookId(),
                request.stars(),
                request.review()
        );

        createRatingUseCase.execute(command);
    }

}
