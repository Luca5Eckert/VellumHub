package com.mrs.recommendation_service.domain.exception.book_feature;

public class BookFeatureNotFoundException extends BookFeatureDomainException {
    public BookFeatureNotFoundException() {
        super("Media feature not found");
    }

    public BookFeatureNotFoundException(String mediaId) {
        super("Media feature not found with ID: " + mediaId);
    }
}
