package com.mrs.recommendation_service.exception.domain.user_profile;

public class UserProfileNotFoundException extends UserProfileDomainException {
    public UserProfileNotFoundException() {
        super("User profile not found");
    }

    public UserProfileNotFoundException(String userId) {
        super("User profile not found with ID: " + userId);
    }
}
