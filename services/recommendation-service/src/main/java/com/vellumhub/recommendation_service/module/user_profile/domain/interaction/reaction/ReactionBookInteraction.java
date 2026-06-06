package com.vellumhub.recommendation_service.module.user_profile.domain.interaction.reaction;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import org.springframework.stereotype.Service;

@Service
public class ReactionBookInteraction {

    public ProfileAdjustment toAdjustment(BookFeature bookFeature, String reactionType) {
        var reaction = Reaction.of(reactionType);

        return new ProfileAdjustment(
                bookFeature.getBookId(),
                reaction.adjustmentValue,
                bookFeature.getEmbedding()
        );
    }

}
