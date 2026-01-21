package com.mrs.user_service.module.user.domain.handler;

import com.mrs.user_service.module.user.domain.page.PageUser;
import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class GetAllUserHandler {

    private final UserRepository userRepository;

    public GetAllUserHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Page<UserEntity> execute(
            PageUser pageUser
    ) {

        PageRequest pageRequest = PageRequest.of(
                pageUser.pageNumber(),
                pageUser.pageSize()
        );

        return userRepository.findAll(pageRequest);
    }


}
