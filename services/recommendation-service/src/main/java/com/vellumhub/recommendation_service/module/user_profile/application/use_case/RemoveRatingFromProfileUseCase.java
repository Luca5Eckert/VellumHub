package com.vellumhub.recommendation_service.module.user_profile.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.rating.RatingBookInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** Compensates the rating score when the canonical rating is deleted. */
@Service
public class RemoveRatingFromProfileUseCase {
    private final UserProfileRepository profiles;
    private final BookFeatureRepository features;
    private final RatingBookInteraction rating;
    public RemoveRatingFromProfileUseCase(UserProfileRepository profiles,BookFeatureRepository features,
                                         RatingBookInteraction rating) {
        this.profiles=profiles;this.features=features;this.rating=rating;
    }
    public void execute(UUID userId,UUID bookId,int oldStars) {
        var feature=features.findById(bookId).orElseThrow(()->
                new IllegalStateException("Book features missing for rating deletion bookId="+bookId));
        UserProfile profile=profiles.findById(userId).orElseGet(()->new UserProfile(userId));
        profile.applyUpdate(rating.toDeletionAdjustment(feature,oldStars));
        profiles.save(profile);
    }
}

