package com.vellumhub.user_service.module.auth.domain.port;

import com.vellumhub.user_service.module.auth.domain.model.UserInfo;

public interface ExternalVerification {
    UserInfo authenticate(String externalToken);
}
