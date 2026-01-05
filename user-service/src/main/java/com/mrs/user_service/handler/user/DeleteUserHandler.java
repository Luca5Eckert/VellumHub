package com.mrs.user_service.handler.user;

import com.mrs.user_service.exception.application.UserNotFoundException;
import com.mrs.user_service.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DeleteUserHandler {

    private final UserRepository userRepository;

    public DeleteUserHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void execute(UUID userId){
        if(!userRepository.existsById(userId)) {
            throw new UserNotFoundException();
        }

        userRepository.deleteById(userId);
    }

}
