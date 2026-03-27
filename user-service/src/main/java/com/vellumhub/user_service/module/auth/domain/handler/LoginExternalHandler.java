package com.mrs.user_service.module.auth.domain.handler;

import com.mrs.user_service.module.auth.domain.exception.AuthDomainException;
import com.mrs.user_service.module.auth.domain.model.UserInfo;
import com.mrs.user_service.module.auth.domain.port.ExternalVerification;
import com.mrs.user_service.module.auth.domain.port.TokenProvider;
import com.mrs.user_service.module.user.domain.RoleUser;
import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoginExternalHandler {

    private final ExternalVerification externalVerification;
    private final TokenProvider tokenProvider;

    private final UserRepository userRepository;

    public LoginExternalHandler(ExternalVerification externalVerification, TokenProvider tokenProvider, UserRepository userRepository) {
        this.externalVerification = externalVerification;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    public String handle(String externalToken) {
        UserInfo userInfo =  externalVerification.authenticate(externalToken);

        UserEntity user = userRepository.findByEmail(userInfo.email())
                .orElseGet(() -> createUser(userInfo));

        return tokenProvider.createToken(
                user.getEmail(),
                user.getId(),
                List.of(user.getRole().name())
        );
    }

    private UserEntity createUser(UserInfo userInfo) {
        var userEntity = UserEntity.builder()
                .name(userInfo.name())
                .email(userInfo.email())
                .role(RoleUser.USER)
                .build();

        return userRepository.save(userEntity);
    }

}
