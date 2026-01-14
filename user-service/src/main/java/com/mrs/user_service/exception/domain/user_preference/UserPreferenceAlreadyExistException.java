package com.mrs.user_service.exception.domain.user_preference;

public class UserPreferenceAlreadyExistException extends UserPreferenceException {
    public UserPreferenceAlreadyExistException(String message) {
        super(message);
    }
}
