package com.vellumhub.user_service.module.user.domain.port;

import com.vellumhub.user_service.module.user.domain.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Includes soft-deleted accounts; SQL uniqueness still reserves those email addresses. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM tb_users WHERE LOWER(email) = LOWER(:email))", nativeQuery = true)
    boolean existsByEmailIncludingDeleted(@Param("email") String email);

    Optional<UserEntity> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForPreferenceUpdate(@Param("id") UUID id);
}
