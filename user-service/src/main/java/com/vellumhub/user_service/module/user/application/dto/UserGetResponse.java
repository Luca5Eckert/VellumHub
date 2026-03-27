package com.mrs.user_service.module.user.application.dto;

import java.util.UUID;

public record UserGetResponse(
        UUID id,
        String name,
        String email
) {
}
