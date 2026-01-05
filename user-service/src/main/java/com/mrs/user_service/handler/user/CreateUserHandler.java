package com.mrs.user_service.handler.user;

import com.mrs.user_service.exception.domain.UserNotUniqueException;
import com.mrs.user_service.model.UserEntity;
import com.mrs. user_service.repository.UserRepository;
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