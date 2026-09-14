package com.vellumhub.engagement_service.module.rating.application.handler;

import com.vellumhub.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.vellumhub.engagement_service.module.rating.application.dto.UpdateRatingRequest;
import com.vellumhub.engagement_service.module.rating.application.mapper.RatingMapper;
import com.vellumhub.engagement_service.module.rating.domain.command.UpdateRatingCommand;
import com.vellumhub.engagement_service.module.rating.domain.producer.UpdatedRatingEventProducer;
import com.vellumhub.engagement_service.module.rating.domain.use_case.UpdateRatingUseCase;
import org.springframework.stereotype.Component;

@Component
public class UpdateRatingHandler {

    private final UpdateRatingUseCase updateRatingUseCase;
    private final RatingMapper mapper;
    private final UpdatedRatingEventProducer updatedRatingEventProducer;

    public UpdateRatingHandler(
            UpdateRatingUseCase updateRatingUseCase,
            RatingMapper mapper,
            UpdatedRatingEventProducer updatedRatingEventProducer
    ) {
        this.updateRatingUseCase = updateRatingUseCase;
        this.mapper = mapper;
        this.updatedRatingEventProducer = updatedRatingEventProducer;
    }

    public RatingGetResponse handle(long ratingId, UpdateRatingRequest request){
        UpdateRatingCommand command = new UpdateRatingCommand(
                ratingId,
                request.stars(),
                request.review()
        );

        var updateResult = updateRatingUseCase.execute(command);
        updatedRatingEventProducer.produce(updateResult);

        return mapper.toGetResponse(updateResult.rating());
    }

}
