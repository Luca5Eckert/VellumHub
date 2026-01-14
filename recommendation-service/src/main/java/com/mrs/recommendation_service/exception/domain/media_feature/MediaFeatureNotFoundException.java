package com.mrs.recommendation_service.exception.domain.media_feature;

public class MediaFeatureNotFoundException extends MediaFeatureDomainException {
    public MediaFeatureNotFoundException() {
        super("Media feature not found");
    }

    public MediaFeatureNotFoundException(String mediaId) {
        super("Media feature not found with ID: " + mediaId);
    }
}
