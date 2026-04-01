package com.vellumhub.recommendation_service.module.user_profile.domain.interaction.reaction;

public enum Reaction {
    VERY_POSITIVE(3.0f),
    POSITIVE(1.5f),
    NEGATIVE(-0.5f);

    public final float adjustmentValue;

    Reaction(float adjustmentValue) {
        this.adjustmentValue = adjustmentValue;
    }

    public static Reaction of(String reactionType) {
        return Reaction.valueOf(reactionType);
    }
}
