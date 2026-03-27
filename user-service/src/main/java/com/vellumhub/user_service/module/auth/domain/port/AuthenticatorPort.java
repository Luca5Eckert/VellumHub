package com.vellumhub.user_service.module.auth.domain.port;

import com.vellumhub.user_service.module.auth.domain.model.AuthenticatedUser;

public interface AuthenticatorPort {
    AuthenticatedUser authenticate(String email, String password);
}