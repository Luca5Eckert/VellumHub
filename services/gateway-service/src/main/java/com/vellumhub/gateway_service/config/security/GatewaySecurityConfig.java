package com.vellumhub.gateway_service.config.security;

import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import com.vellumhub.security.jwt.JwtAccessToken;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;
import java.util.Arrays;
import java.util.List;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {
    @Value("${JWT_JWKS_URI:http://user-service:8080/.well-known/jwks.json}")
    private String jwksUri = "http://user-service:8080/.well-known/jwks.json";

    @Value("${JWT_ISSUER:https://auth.vellumhub.local}")
    private String issuer = "https://auth.vellumhub.local";

    @Value("${JWT_AUDIENCE:api://vellumhub}")
    private String audience = "api://vellumhub";

    @Value("${JWT_CLOCK_SKEW_SECONDS:30}")
    private long clockSkewSeconds = 30;

    private final Resource publicKeyResource;
    private final boolean publicDocumentation;
    private final String allowedOrigins;

    public GatewaySecurityConfig(
            @Value("${jwt.public-key-location}") Resource publicKeyResource,
            @Value("${VELLUM_DOCS_ENABLED:false}") boolean publicDocumentation,
            @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:3001}") String allowedOrigins) {
        this.publicKeyResource = publicKeyResource;
        this.publicDocumentation = publicDocumentation;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) throws IOException {
        ServerAuthenticationEntryPoint authenticationEntryPoint = (exchange, error) -> {
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = "{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid access token\"}".getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        };
        ServerAccessDeniedHandler accessDeniedHandler = (exchange, error) -> {
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = "{\"code\":\"FORBIDDEN\",\"message\":\"Insufficient permission\"}".getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        };

        return http
                // Stateless bearer-token API. Revisit CSRF if browser cookies become authenticators.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsSource()))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/api/v1/**").permitAll()
                        .pathMatchers("/api/v1/internal/**", "/api/v1/users/internal/**").denyAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/login",
                                "/api/v1/auth/register", "/api/v1/auth/google").permitAll()
                        .pathMatchers("/docs", "/docs/**", "/swagger-ui/**",
                                "/v3/api-docs/**").access((authentication, context) ->
                                Mono.just(new AuthorizationDecision(publicDocumentation)))
                        .anyExchange().authenticated()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder())))
                .build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isBlank()).toList();
        if (origins.isEmpty() || origins.stream().anyMatch(origin -> origin.equals("*"))) {
            throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS must contain explicit origins");
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .validateType(false)
                .build();
        decoder.setJwtValidator(JwtAccessToken.validator(issuer, audience, clockSkewSeconds));
        // A trusted JWKS outage with an unknown kid must be an authentication failure (401),
        // not an unhandled infrastructure error (500). Cached known keys remain usable.
        // Nimbus can throw before returning a Mono (e.g. when a kid is missing and
        // the configured JWKS endpoint is unavailable). Defer decoding so both
        // synchronous and reactive key-fetch failures become invalid-token errors.
        return token -> Mono.defer(() -> decoder.decode(token))
                .onErrorMap(IllegalStateException.class, failure ->
                        new BadJwtException("Unable to verify JWT signing key", failure));
    }
}
