package com.vellumhub.user_service.module.user.domain.handler;

import com.vellumhub.user_service.module.user.domain.exception.UserNotUniqueException;
import com.vellumhub.user_service.module.user.domain.UserEntity;
import com.vellumhub.user_service.module.user.domain.port.UserRepository;
import com.vellumhub.user_service.share.metrics.VellumHubMetrics;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateUserHandler {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VellumHubMetrics metrics;

    public CreateUserHandler(UserRepository userRepository, PasswordEncoder passwordEncoder, VellumHubMetrics metrics) {
        this.userRepository = userRepository;
        this. passwordEncoder = passwordEncoder;
        this.metrics = metrics;
    }

    @Transactional
    public void execute(UserEntity user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new UserNotUniqueException();
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        userRepository.save(user);
        metrics.recordBusinessCounter(VellumHubMetrics.USERS_CREATED, "admin_user_creation", "success");
    }
}
