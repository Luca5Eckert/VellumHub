package com.mrs.user_service.module.user.domain.handler;

import com.mrs.user_service.module.user.domain.exception.UserNotUniqueException;
import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateUserHandler {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CreateUserHandler(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this. passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void execute(UserEntity user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new UserNotUniqueException();
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        userRepository.save(user);
    }
}