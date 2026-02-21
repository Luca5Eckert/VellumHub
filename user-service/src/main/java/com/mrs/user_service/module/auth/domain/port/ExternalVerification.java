package com.mrs.user_service.module.auth.domain.port;

import com.mrs.user_service.module.auth.domain.model.UserInfo;

public interface ExternalVerification {
    UserInfo authenticate(String externalToken);
}
