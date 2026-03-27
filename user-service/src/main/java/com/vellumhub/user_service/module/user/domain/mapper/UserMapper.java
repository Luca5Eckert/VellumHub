package com.mrs.user_service.module.user.domain.mapper;


import com.mrs.user_service.module.user.application.dto.UserGetResponse;
import com.mrs.user_service.module.user.domain.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserGetResponse toGetResponse(UserEntity user){
        return new UserGetResponse(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }

}
