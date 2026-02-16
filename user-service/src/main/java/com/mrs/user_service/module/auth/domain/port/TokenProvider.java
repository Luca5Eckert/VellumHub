package com.mrs.user_service.module.auth.domain.port;

import java.util.List;
import java.util.UUID;

public interface TokenProvider {

    public String createToken(String email, UUID userId, List<String> roles);

}
