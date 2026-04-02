package com.vellumhub.recommendation_service.module.user_profile.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.exception.BookFeatureNotFoundException;
import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateUserProfileWithRatingCommand;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.rating.RatingBookInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import org.springframework.stereotype.Component;

@Component
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
        UserProfile profile = userProfileRepository.findById(command.userId())
                .orElseGet(() -> new UserProfile(command.userId()));

        BookFeature book = bookFeatureRepository.findById(command.bookId())
                .orElseThrow(() -> new BookFeatureNotFoundException("Book features not found"));

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