package com.mrs.user_service.dto;

public record UpdateUserRequest(
        String name,
        String email,
        String fullname
) {
}
