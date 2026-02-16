package com.mrs.recommendation_service.module.user_profile.domain.exception.user_profile;

public class UserProfileNotFoundException extends UserProfileDomainException {
    public UserProfileNotFoundException() {
        super("User profile not found");
    }

    public UserProfileNotFoundException(String userId) {
        super("User profile not found with ID: " + userId);
    }
}
