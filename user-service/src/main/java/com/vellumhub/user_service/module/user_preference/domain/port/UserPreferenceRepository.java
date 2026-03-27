package com.mrs.user_service.module.user_preference.domain.port;

import com.mrs.user_service.module.user_preference.domain.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {
    boolean existsByUserId(UUID userId);
}
