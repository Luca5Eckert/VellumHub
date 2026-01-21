package com.mrs.user_service.share.security.token;

import org.springframework.security.core. GrantedAuthority;
import java.util.Collection;
import java.util.UUID;

public interface TokenProvider {

    String createToken(String email, UUID userId, Collection<? extends GrantedAuthority> grantedAuthorities);

}