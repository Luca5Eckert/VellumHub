package com.vellumhub.recommendation_service.module.user_profile.domain.interaction.reaction;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.BookInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;

public class ReactionBookInteraction implements BookInteraction {

    private final String reactionType;

    public ReactionBookInteraction(String reactionType) {
        this.reactionType = reactionType;
    }

    @Override
    public ProfileAdjustment toAdjustment(BookFeature bookFeature) {
        var reaction = Reaction.of(reactionType);

        return new ProfileAdjustment(
                bookFeature.getBookId(),
                reaction.adjustmentValue,
                bookFeature.getEmbedding()
        );
    }

}
