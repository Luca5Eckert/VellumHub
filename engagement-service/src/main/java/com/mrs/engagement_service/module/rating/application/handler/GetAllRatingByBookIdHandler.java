package com.mrs.engagement_service.module.rating.application.handler;

import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.application.mapper.RatingMapper;
import com.mrs.engagement_service.module.rating.domain.command.GetAllRatingByBookCommand;
import com.mrs.engagement_service.module.rating.domain.use_case.GetAllRatingByBookUseCase;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GetAllRatingByBookIdHandler {

    private final GetAllRatingByBookUseCase getAllRatingByBookUseCase;
    private final RatingMapper mapper;

    public GetAllRatingByBookIdHandler(GetAllRatingByBookUseCase getAllRatingByBookUseCase, RatingMapper mapper) {
        this.getAllRatingByBookUseCase = getAllRatingByBookUseCase;
        this.mapper = mapper;
    }

    public List<RatingGetResponse> handle(
            UUID bookId, int page, int size
    ) {
        var command = GetAllRatingByBookCommand.of(bookId, page, size);

        var responses = getAllRatingByBookUseCase.execute(command);

        return responses.stream()
                .map(mapper::toGetResponse)
                .toList();
    }

}
