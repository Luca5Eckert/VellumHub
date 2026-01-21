package com.mrs.user_service.module.user.domain.handler;

import com.mrs.user_service.module.user.application.exception.UserNotFoundException;
import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class GetUserHandler {

    private final UserRepository userRepository;

    public GetUserHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserEntity execute(UUID userId){
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

}
