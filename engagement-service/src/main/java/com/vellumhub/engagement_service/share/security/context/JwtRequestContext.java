package com.vellumhub.engagement_service.share.security.context;

import com.vellumhub.engagement_service.share.exception.UserNotAuthenticatedException;
import com.vellumhub.engagement_service.share.port.RequestContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public class JwtRequestContext implements RequestContext {

    @Override
    public UUID getUserId() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        if (principal instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString("user_id");
            return UUID.fromString(userId);
        }

        throw new UserNotAuthenticatedException("User not authenticated or invalid token");
    }

}
