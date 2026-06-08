package com.vellumhub.recommendation_service.module.user_profile.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateUserProfileWithRatingCommand;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.rating.RatingBookInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UpdateUserProfileWithRatingUseCase {

    private final UserProfileRepository userProfileRepository;
    private final BookFeatureRepository bookFeatureRepository;

    private final RatingBookInteraction ratingBookInteraction;

    public UpdateUserProfileWithRatingUseCase(UserProfileRepository userProfileRepository, BookFeatureRepository bookFeatureRepository, RatingBookInteraction ratingBookInteraction) {
        this.userProfileRepository = userProfileRepository;
        this.bookFeatureRepository = bookFeatureRepository;
        this.ratingBookInteraction = ratingBookInteraction;
    }

    public void execute(UpdateUserProfileWithRatingCommand command) {
        BookFeature book = bookFeatureRepository.findById(command.bookId())
                .orElse(null);
        if (book == null) {
            log.warn(
                    "Skipping rating profile update because book features are not available yet. UserId={}, BookId={}",
                    command.userId(),
                    command.bookId()
            );
            return;
        }

        UserProfile profile = userProfileRepository.findById(command.userId())
                .orElseGet(() -> new UserProfile(command.userId()));
        ProfileAdjustment profileAdjustment = ratingBookInteraction.toAdjustment(
                book,
                command.oldStars(),
                command.newStars(),
                command.isNewRating()
        );

        profile.applyUpdate(profileAdjustment);

        userProfileRepository.save(profile);
    }


}
