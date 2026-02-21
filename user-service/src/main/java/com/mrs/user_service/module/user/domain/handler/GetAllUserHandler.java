package com.mrs.user_service.module.user.domain.handler;

import com.mrs.user_service.module.user.domain.page.PageUser;
import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetAllUserHandler {

    private final UserRepository userRepository;

    public GetAllUserHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserEntity> execute(PageUser pageUser) {
        PageRequest pageRequest = PageRequest.of(
                pageUser.pageNumber(),
                pageUser.pageSize(),
                Sort.by(Sort.Direction.ASC, "createdAt")
        );

        return userRepository.findAll(pageRequest);
    }

}
