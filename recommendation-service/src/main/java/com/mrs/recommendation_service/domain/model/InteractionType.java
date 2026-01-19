package com.mrs.recommendation_service.domain.model;

public enum InteractionType {
    LIKE(2),
    DISLIKE(-2),
    WATCH(0.75);

    double weightInteraction;

    InteractionType(double weightInteraction){
        this.weightInteraction = weightInteraction;
    }

    public double getWeightInteraction(){
        return weightInteraction;
    }

}
