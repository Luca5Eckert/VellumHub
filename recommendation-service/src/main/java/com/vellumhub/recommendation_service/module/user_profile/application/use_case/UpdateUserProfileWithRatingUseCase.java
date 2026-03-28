package com.vellumhub.recommendation_service.module.user_profile.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateUserProfileWithRatingCommand;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.BookInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.RatingBookInteration;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
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

        BookFeature book = bookFeatureRepository.findById(command.bookId())
                .orElseThrow(() -> new RuntimeException("Book features not found"));

        ProfileAdjustment profileAdjustment = getProfileAdjustment(
                command.oldStars(),
                command.newStars(),
                command.isNewRating(),
                book
        );

        profile.applyUpdate(profileAdjustment);

        userProfileRepository.save(profile);
    }

    public ProfileAdjustment getProfileAdjustment(
            int oldStars,
            int newStars,
            boolean isNewRating,
            BookFeature book
    ) {
        BookInteraction bookInteraction = new RatingBookInteration(oldStars, newStars, isNewRating);

        return bookInteraction.toAdjustment(book);
    }

}