package com.mrs.recommendation_service.module.book_feature.domain.exception;

public class BookFeatureNotFoundException extends BookFeatureDomainException {
    public BookFeatureNotFoundException() {
        super("Media feature not found");
    }

    public BookFeatureNotFoundException(String mediaId) {
        super("Media feature not found with ID: " + mediaId);
    }
}
