package com.mrs.user_service.module.user.domain.handler;

import com.mrs.user_service.module.user.application.dto.UpdateUserRequest;
import com.mrs.user_service.module.user.application.exception.UserNotFoundException;
import com.mrs.user_service.module.user.domain.exception.EmailAlreadyInUseException;
import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
public class UpdateUserHandler {

    private final UserRepository userRepository;

    public UpdateUserHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void execute(UUID userId, UpdateUserRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        updateName(user, request.name());
        updateEmail(user, request.email());

        userRepository.save(user);
    }

    private void updateName(UserEntity user, String newName) {
        if (StringUtils.hasText(newName)) {
            user.setName(newName);
        }
    }

    private void updateEmail(UserEntity user, String newEmail) {
        if (!StringUtils.hasText(newEmail)) {
            return;
        }
        if (newEmail.equals(user.getEmail())) {
            return;
        }

        if (userRepository.existsByEmail(newEmail)) {
            throw new EmailAlreadyInUseException(newEmail);
        }

        user.setEmail(newEmail);
    }
}