package com.vellumhub.user_service.module.user_preference.domain.port;

import com.vellumhub.user_service.module.user_preference.domain.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {
    boolean existsByUserId(UUID userId);

    Optional<UserPreference> findByUserId(UUID userId);
}
