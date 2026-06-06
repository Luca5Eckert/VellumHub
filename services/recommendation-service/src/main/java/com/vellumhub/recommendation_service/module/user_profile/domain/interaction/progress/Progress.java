package com.vellumhub.recommendation_service.module.user_profile.domain.interaction.progress;

public enum Progress {
    WANT_TO_READ(0.5f),
    READING(1.0f),
    COMPLETED(2.0f);

    public final float adjusment;

    Progress(float adjusment){
        this.adjusment = adjusment;
    }

    public static Progress of(String progress) {
        return Progress.valueOf(progress);
    }

}
