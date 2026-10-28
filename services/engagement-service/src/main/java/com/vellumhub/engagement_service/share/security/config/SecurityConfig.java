package com.vellumhub.engagement_service.share.security.config;

import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import com.vellumhub.security.jwt.JwtAccessToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;

/**
 * Configuração central de segurança para o microserviço.
 * <p>
 * Esta classe define como o serviço protege seus endpoints HTTP, atuando como um
 * <b>OAuth2 Resource Server</b>. Ela é responsável por interceptar requisições,
 * validar tokens JWT recebidos no cabeçalho Authorization e converter as permissões
 * (roles) contidas no token para o contexto de segurança do Spring.
 * </p>
 * * Principais responsabilidades:
 * <ul>
 * <li>Desabilitar CSRF e gerenciamento de sessão (Stateless).</li>
 * <li>Exigir autenticação para endpoints protegidos.</li>
 * <li>Configurar o decodificador JWT (com chave publica RSA).</li>
 * <li>Converter claims do JWT para {@code GrantedAuthority}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${JWT_JWKS_URI:http://auth-service:9000/oauth2/jwks}")
    private String jwksUri;

    @Value("${JWT_ISSUER:https://auth.vellumhub.local}")
    private String issuer;

    @Value("${JWT_AUDIENCE:api://vellumhub}")
    private String audience;

    @Value("${JWT_CLOCK_SKEW_SECONDS:30}")
    private long clockSkewSeconds;


    /**
     * Define a cadeia de filtros de segurança HTTP (Security Filter Chain).
     *
     * @param http O construtor de segurança HTTP do Spring.
     * @return A cadeia de filtros configurada.
     * @throws Exception Caso ocorra erro na configuração.
     */
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
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
                        .access((authentication, context) -> new AuthorizationDecision(documentationEnabled))
                        .anyRequest().authenticated()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                )
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
        // Treat JWKS retrieval errors as invalid authentication while retaining trusted cache.
        return token -> {
            try {
                return decoder.decode(token);
            } catch (IllegalStateException failure) {
                throw new BadJwtException("Unable to verify JWT signing key", failure);
            }
        };
    }

}
