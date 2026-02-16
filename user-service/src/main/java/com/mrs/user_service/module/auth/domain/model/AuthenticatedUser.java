package com.mrs.user_service.module.auth.domain.model;

import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String email,
        List<String> roles
) {}