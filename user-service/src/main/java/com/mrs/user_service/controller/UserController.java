package com.mrs.user_service.controller;

import com.mrs.user_service.dto.CreateUserRequest;
import com.mrs.user_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public ResponseEntity<String> create(@RequestBody @Valid CreateUserRequest createUserRequest){
        userService.create(createUserRequest);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body("User created with success");
    }

}
