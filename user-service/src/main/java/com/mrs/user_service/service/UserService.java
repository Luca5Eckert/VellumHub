package com.mrs.user_service.service;

import com.mrs.user_service.dto.CreateUserRequest;
import com.mrs.user_service.handler.CreateUserHandler;
import com.mrs.user_service.model.UserEntity;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final CreateUserHandler createUserHandler;

    public UserService(CreateUserHandler createUserHandler) {
        this.createUserHandler = createUserHandler;
    }

    public void create(CreateUserRequest createUserRequest){
        UserEntity user = new UserEntity(
                createUserRequest.name(),
                createUserRequest.email(),
                createUserRequest.password()
        );

        createUserHandler.handler(user);
    }

}
