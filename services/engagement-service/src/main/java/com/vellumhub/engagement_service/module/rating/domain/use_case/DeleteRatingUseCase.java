package com.vellumhub.engagement_service.module.rating.domain.use_case;

import com.vellumhub.engagement_service.module.rating.domain.command.DeleteRatingCommand;
import com.vellumhub.engagement_service.module.rating.domain.exception.RatingDomainException;
import com.vellumhub.engagement_service.module.rating.domain.exception.RatingNotFoundException;
import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import com.vellumhub.engagement_service.module.rating.application.event.RatingEventRecorder;

@Component
public class DeleteRatingUseCase {

    private final RatingRepository ratingRepository;
    private final RatingEventRecorder recorder;

    public DeleteRatingUseCase(RatingRepository ratingRepository, RatingEventRecorder recorder) {
        this.ratingRepository = ratingRepository;
        this.recorder = recorder;
    }

    @Transactional
    public void execute(DeleteRatingCommand command) {
        Rating rating = ratingRepository.findByIdForUpdate(command.ratingId())
                .orElseThrow(() -> new RatingNotFoundException("Rating not found with id: " + command.ratingId()));

        if(!Objects.equals(rating.getUserId(),command.userId())){
            throw new RatingDomainException("User not authorized to delete this rating");
        }

        ratingRepository.deleteById(command.ratingId());
        recorder.deleted(rating);
    }

}

