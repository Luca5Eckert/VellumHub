package com.mrs.user_service.share.security.token;

import io. jsonwebtoken.Jwts;
import io.jsonwebtoken. SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security. Keys;
import org.springframework. beans.factory.annotation.Value;
import org.springframework.security. core.GrantedAuthority;
import org.springframework.stereotype. Component;

import java.security. Key;
import java.util. Collection;
import java.util. Date;
import java.util. List;
import java.util.UUID;

@Component
public class JwtTokenProvider implements TokenProvider {

    private final Key key;
    private final long validityInMilliseconds;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration}") long validityInMilliseconds) {

        this.validityInMilliseconds = validityInMilliseconds;
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String createToken(String email, UUID userId, Collection<? extends GrantedAuthority> grantedAuthorities) {  // ADICIONAR userId AQUI
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        List<String> roles = grantedAuthorities.stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .setSubject(email)
                .claim("user_id", userId.toString())
                .claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}