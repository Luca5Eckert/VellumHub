package com.mrs.recommendation_service.module.user_profile.domain.model;

public enum RatingCategory {
    DETRACTOR(-5), NEUTRAL(1), PROMOTER(5);

    private final int weight;

    RatingCategory(int weight) { this.weight = weight; }

    public static RatingCategory fromStars(int stars) {
        if (stars <= 2) return DETRACTOR;
        if (stars == 3) return NEUTRAL;
        return PROMOTER;
    }

    public int getWeight() { return weight; }
}