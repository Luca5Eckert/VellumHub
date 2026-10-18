package com.vellumhub.engagement_service.share.security.config;

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

    @Value("${jwt.public-key-location}")
    private Resource publicKeyResource;

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
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                )
                .build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

    @Bean
    public JwtDecoder jwtDecoder() throws IOException {
        RSAPublicKey publicKey;
        try (InputStream inputStream = publicKeyResource.getInputStream()) {
            publicKey = Objects.requireNonNull(RsaKeyConverters.x509().convert(inputStream),
                    "Invalid X.509 RSA public key");
        }
        if (publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("JWT verification RSA key must have at least 2048 bits");
        }
        return NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }

}
