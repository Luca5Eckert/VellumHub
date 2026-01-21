package com.mrs.user_service.module.auth.domain.service;

import com.mrs.user_service.module.auth.application.dto.LoginUserRequest;
import com.mrs.user_service.module.auth.application.dto.RegisterUserRequest;
import com.mrs.user_service.module.auth.domain.handler.LoginUserHandler;
import com.mrs.user_service.module.auth.domain.handler.RegisterUserHandler;
import com.mrs.user_service.module.user.domain.RoleUser;
import com.mrs.user_service.module.user.domain.UserEntity;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final LoginUserHandler loginUserHandler;
    private final RegisterUserHandler registerUserHandler;

    public AuthService(LoginUserHandler loginUserHandler, RegisterUserHandler registerUserHandler) {
        this.loginUserHandler = loginUserHandler;
        this.registerUserHandler = registerUserHandler;
    }

    public void register(RegisterUserRequest registerUserRequest) {
        UserEntity user = UserEntity.builder()
                .name(registerUserRequest.name())
                .email(registerUserRequest.email())
                .password(registerUserRequest.password())
                .role(RoleUser.USER)
                .build();

        registerUserHandler.execute(user);
    }

    public String login(LoginUserRequest loginUserRequest){
        return loginUserHandler.execute(
                loginUserRequest.email(),
                loginUserRequest.password()
        );
    }


}
