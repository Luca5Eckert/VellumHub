package com.mrs.engagement_service.module.rating.application.handler;

import com.mrs.engagement_service.module.rating.application.dto.CreateRatingRequest;
import com.mrs.engagement_service.module.rating.domain.producer.CreatedRatingEventProducer;
import com.mrs.engagement_service.module.rating.domain.command.CreateRatingCommand;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.use_case.CreateRatingUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateRatingHandler {

    private final CreateRatingUseCase createRatingUseCase;
    private final CreatedRatingEventProducer createdRatingEventProducer;

    public CreateRatingHandler(CreateRatingUseCase createRatingUseCase, CreatedRatingEventProducer createdRatingEventProducer) {
        this.createRatingUseCase = createRatingUseCase;
        this.createdRatingEventProducer = createdRatingEventProducer;
    }

    @Transactional
    public void handle(CreateRatingRequest request){
        CreateRatingCommand command = new CreateRatingCommand(
                request.userId(),
                request.bookId(),
                request.stars(),
                request.review()
        );

        Rating rating = createRatingUseCase.execute(command);


        createdRatingEventProducer.produce(rating);
    }

}
