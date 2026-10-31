package com.vellumhub.user_service.share.security.config;

import com.vellumhub.security.jwt.JwtAccessToken;
import com.vellumhub.security.jwt.HumanApiAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    @Value("${JWT_JWKS_URI:http://auth-service:9000/oauth2/jwks}")
    private String jwksUri;

    @Value("${JWT_ISSUER:https://auth.vellumhub.local}")
    private String issuer;

    @Value("${JWT_AUDIENCE:api://vellumhub}")
    private String audience;

    @Value("${JWT_CLOCK_SKEW_SECONDS:30}")
    private long clockSkewSeconds;

    @Value("${VELLUM_DOCS_ENABLED:false}")
    private boolean documentationEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, error) -> {
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid access token\"}");
        };
        AccessDeniedHandler accessDeniedHandler = (request, response, error) -> {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Insufficient permission\"}");
        };

        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
                        .access((authentication, context) -> new AuthorizationDecision(documentationEnabled))
                        .requestMatchers(HttpMethod.GET, "/users")
                        .access((authentication, context) -> new AuthorizationDecision(
                                HumanApiAccess.allowed(authentication.get(), "user:read", true)))
                        .requestMatchers(HttpMethod.GET, "/users/*")
                        .access((authentication, context) -> new AuthorizationDecision(
                                HumanApiAccess.allowed(authentication.get(), "user:read", false)))
                        .requestMatchers(HttpMethod.PUT, "/users/*")
                        .access((authentication, context) -> new AuthorizationDecision(
                                HumanApiAccess.allowed(authentication.get(), "user:write", true)))
                        .requestMatchers(HttpMethod.DELETE, "/users/*")
                        .access((authentication, context) -> new AuthorizationDecision(
                                HumanApiAccess.allowed(authentication.get(), "user:write", true)))
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        return JwtAccessToken.servletConverter();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .validateType(false)
                .build();
        decoder.setJwtValidator(JwtAccessToken.validator(issuer, audience, clockSkewSeconds));
        return token -> {
            try {
                return decoder.decode(token);
            } catch (IllegalStateException failure) {
                throw new BadJwtException("Unable to verify JWT signing key", failure);
            }
        };
    }
}
