package com.mrs.engagement_service.module.rating.application.handler;

import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.application.mapper.RatingMapper;
import com.mrs.engagement_service.module.rating.domain.command.GetUserRatingCommand;
import com.mrs.engagement_service.module.rating.domain.use_case.GetUserRatingUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class GetUserRatingHandler {

    private final GetUserRatingUseCase getUserRatingUseCase;

    private final RatingMapper ratingMapper;

    public GetUserRatingHandler(GetUserRatingUseCase getUserRatingUseCase, RatingMapper ratingMapper) {
        this.getUserRatingUseCase = getUserRatingUseCase;
        this.ratingMapper = ratingMapper;
    }


    @Transactional(readOnly = true)
    public List<RatingGetResponse> handle(
            UUID userId,
            Integer minStars,
            Integer maxStars,
            OffsetDateTime from,
            OffsetDateTime to,
            int pageNumber,
            int pageSize
    ) {

        GetUserRatingCommand getUserRatingCommand = new GetUserRatingCommand(
                userId,
                minStars,
                maxStars,
                from,
                to,
                pageNumber,
                pageSize
        );

        var ratingList = getUserRatingUseCase.execute(getUserRatingCommand);

        return ratingList.stream()
                .map(ratingMapper::toGetResponse)
                .toList();
    }


}
