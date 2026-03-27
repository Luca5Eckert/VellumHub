package com.mrs.engagement_service.module.rating.application.handler;

import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.application.dto.UpdateRatingRequest;
import com.mrs.engagement_service.module.rating.application.mapper.RatingMapper;
import com.mrs.engagement_service.module.rating.domain.command.UpdateRatingCommand;
import com.mrs.engagement_service.module.rating.domain.use_case.UpdateRatingUseCase;
import org.springframework.stereotype.Component;

@Component
public class UpdateRatingHandler {

    private final UpdateRatingUseCase updateRatingUseCase;

    private final RatingMapper mapper;

    public UpdateRatingHandler(UpdateRatingUseCase updateRatingUseCase, RatingMapper mapper) {
        this.updateRatingUseCase = updateRatingUseCase;
        this.mapper = mapper;
    }

    public RatingGetResponse handle(long ratingId, UpdateRatingRequest request){
        UpdateRatingCommand command = new UpdateRatingCommand(
                ratingId,
                request.stars(),
                request.review()
        );

        var ratingUpdated = updateRatingUseCase.execute(command);

        return mapper.toGetResponse(ratingUpdated);
    }

}
