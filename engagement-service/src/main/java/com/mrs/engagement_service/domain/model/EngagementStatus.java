package com.mrs.engagement_service.domain.model;

public interface EngagementStatus {

    long getTotalViews();
    long getTotalLikes();
    long getTotalDislikes();
    double getAverageRating();
    long getTotalRatings();
    long getTotalInteractions();
    double getPopularityScore();

}
