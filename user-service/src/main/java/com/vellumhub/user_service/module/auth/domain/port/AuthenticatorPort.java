package com.mrs.user_service.module.auth.domain.port;

import com.mrs.user_service.module.auth.domain.model.AuthenticatedUser;

public interface AuthenticatorPort {
    AuthenticatedUser authenticate(String email, String password);
}