package com.mrs.recommendation_service.module.user_profile.domain.port;

import com.mrs.recommendation_service.module.user_profile.domain.model.UserProfile;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository {
    Optional<UserProfile> findById(UUID uuid);

    void save(UserProfile userProfile);
}