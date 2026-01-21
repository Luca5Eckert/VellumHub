package com.mrs.recommendation_service.infrastructure.repository.user_profile;

import com.mrs.recommendation_service.domain.model.UserProfile;
import com.mrs.recommendation_service.domain.port.UserProfileRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserProfileRepositoryAdapter implements UserProfileRepository {

    private final JpaUserProfileRepository jpaUserProfileRepository;

    public UserProfileRepositoryAdapter(JpaUserProfileRepository jpaUserProfileRepository) {
        this.jpaUserProfileRepository = jpaUserProfileRepository;
    }

    @Override
    public Optional<UserProfile> findById(UUID uuid) {
        return jpaUserProfileRepository.findById(uuid);
    }

    @Override
    public void save(UserProfile userProfile) {
        jpaUserProfileRepository.save(userProfile);
    }
}
