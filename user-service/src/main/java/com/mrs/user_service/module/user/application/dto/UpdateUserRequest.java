package com.mrs.user_service.module.user.application.dto;

public record UpdateUserRequest(
        String name,
        String email
) {
}
