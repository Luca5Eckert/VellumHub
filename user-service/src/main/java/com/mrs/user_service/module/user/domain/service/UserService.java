package com.mrs.user_service.module.user.domain.service;

import com.mrs.user_service.module.user.application.dto.CreateUserRequest;
import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.handler.*;
import com.mrs.user_service.module.user.domain.mapper.UserMapper;
import com.mrs.user_service.module.user.domain.page.PageUser;
import com.mrs.user_service.module.user.application.dto.UpdateUserRequest;
import com.mrs.user_service.module.user.application.dto.UserGetResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final CreateUserHandler createUserHandler;
    private final DeleteUserHandler deleteUserHandler;
    private final UpdateUserHandler updateUserHandler;
    private final GetUserHandler getUserHandler;
    private final GetAllUserHandler getAllUserHandler;

    private final UserMapper userMapper;

    public UserService(CreateUserHandler createUserHandler, DeleteUserHandler deleteUserHandler, UpdateUserHandler updateUserHandler, GetUserHandler getUserHandler, GetAllUserHandler getAllUserHandler, UserMapper userMapper) {
        this.createUserHandler = createUserHandler;
        this.deleteUserHandler = deleteUserHandler;
        this.updateUserHandler = updateUserHandler;
        this.getUserHandler = getUserHandler;
        this.getAllUserHandler = getAllUserHandler;
        this.userMapper = userMapper;
    }

    public void create(CreateUserRequest createUserRequest) {
        UserEntity user = UserEntity.builder()
                .name(createUserRequest.name())
                .email(createUserRequest.email())
                .password(createUserRequest.password())
                .role(createUserRequest.role())
                .build();

        createUserHandler.execute(user);
    }

    public void delete(UUID userId){
        deleteUserHandler.execute(userId);
    }

    public void update(UUID userId, UpdateUserRequest updateUserRequest){
        updateUserHandler.execute(userId, updateUserRequest);
    }

    public UserGetResponse get(UUID userId){
        UserEntity user = getUserHandler.execute(userId);

        return userMapper.toGetResponse(user);
    }

    public List<UserGetResponse> getAll(int pageNumber, int pageSize){
        PageUser pageUser = new PageUser(pageNumber, pageSize);

        Page<UserEntity> users = getAllUserHandler.execute(pageUser);

        return users.stream().map(userMapper::toGetResponse).toList();
    }


}
