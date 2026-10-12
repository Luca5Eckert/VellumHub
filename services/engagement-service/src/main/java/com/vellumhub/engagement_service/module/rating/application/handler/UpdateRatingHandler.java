package com.vellumhub.engagement_service.module.rating.application.handler;

import com.vellumhub.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.vellumhub.engagement_service.module.rating.application.dto.UpdateRatingRequest;
import com.vellumhub.engagement_service.module.rating.application.mapper.RatingMapper;
import com.vellumhub.engagement_service.module.rating.domain.command.UpdateRatingCommand;
import com.vellumhub.engagement_service.module.rating.application.event.RatingEventRecorder;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import com.vellumhub.engagement_service.module.rating.domain.use_case.UpdateRatingUseCase;
import org.springframework.stereotype.Component;

@Component
public class UpdateRatingHandler {

    private final UpdateRatingUseCase updateRatingUseCase;
    private final RatingMapper mapper;
    private final RatingEventRecorder recorder;

    public UpdateRatingHandler(
            UpdateRatingUseCase updateRatingUseCase,
            RatingMapper mapper,
            RatingEventRecorder recorder
    ) {
        this.updateRatingUseCase = updateRatingUseCase;
        this.mapper = mapper;
        this.recorder = recorder;
    }

    @Transactional
    public RatingGetResponse handle(long ratingId, UpdateRatingRequest request, UUID authenticatedUserId){
        UpdateRatingCommand command = new UpdateRatingCommand(
                ratingId,
                request.stars(),
                request.review()
        );

        var updateResult = updateRatingUseCase.execute(command, authenticatedUserId);
        recorder.updated(updateResult);

        return mapper.toGetResponse(updateResult.rating());
    }

}

