package com.vellumhub.recommendation_service.module.user_profile.domain.interaction.reaction;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import org.springframework.stereotype.Service;

@Service
public class ReactionBookInteraction {

    public ProfileAdjustment toAdjustment(
            BookFeature bookFeature,
            String oldReactionType,
            String newReactionType
    ) {
        Reaction newReaction = Reaction.of(newReactionType);
        float oldWeight = oldReactionType == null
                ? 0.0f
                : Reaction.of(oldReactionType).adjustmentValue;
        float adjustment = newReaction.adjustmentValue - oldWeight;

        return ProfileAdjustment.of(
                bookFeature.getBookId(),
                adjustment,
                bookFeature.getEmbedding()
        );
    }
}
