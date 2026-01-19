package com.mrs.recommendation_service.infrastructure.repository;

import com.mrs.recommendation_service.domain.model.UserProfile;
import org.springframework.data. jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}