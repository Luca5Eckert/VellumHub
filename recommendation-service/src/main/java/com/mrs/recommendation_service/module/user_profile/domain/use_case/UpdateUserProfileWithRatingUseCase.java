package com.mrs.recommendation_service.module.user_profile.domain.use_case;

import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.mrs.recommendation_service.module.user_profile.domain.command.UpdateUserProfileWithRatingCommand;
import com.mrs.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.mrs.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UpdateUserProfileWithRatingUseCase {

    private final UserProfileRepository userProfileRepository;
    private final BookFeatureRepository bookFeatureRepository;

    public UpdateUserProfileWithRatingUseCase(UserProfileRepository userProfileRepository, BookFeatureRepository bookFeatureRepository) {
        this.userProfileRepository = userProfileRepository;
        this.bookFeatureRepository = bookFeatureRepository;
    }

    @Transactional
    public void execute(UpdateUserProfileWithRatingCommand command) {
        UserProfile profile = userProfileRepository.findById(command.userId())
                .orElseGet(() -> new UserProfile(command.userId()));

        BookFeature book = bookFeatureRepository.findById(command.mediaId())
                .orElseThrow(() -> new RuntimeException("Book features not found"));

        profile.updateScoreByRating(command);

        profile.applyVectorAdjustment(book.getEmbedding(), command.getWeightAdjustment());

        userProfileRepository.save(profile);
    }

}