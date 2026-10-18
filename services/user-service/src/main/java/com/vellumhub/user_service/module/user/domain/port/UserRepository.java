package com.vellumhub.user_service.module.user.domain.port;

import com.vellumhub.user_service.module.user.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
