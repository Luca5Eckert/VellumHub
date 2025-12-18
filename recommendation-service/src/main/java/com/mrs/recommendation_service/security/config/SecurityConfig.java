package com.mrs.recommendation_service.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

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
 * <li>Configurar o decodificador JWT (com chave simétrica).</li>
 * <li>Converter claims do JWT para {@code GrantedAuthority}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String secretKey;

    /**
     * Define a cadeia de filtros de segurança HTTP (Security Filter Chain).
     *
     * @param http O construtor de segurança HTTP do Spring.
     * @return A cadeia de filtros configurada.
     * @throws Exception Caso ocorra erro na configuração.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
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

    /**
     * Cria o decodificador de JWT responsável por validar a integridade do token.
     * <p>
     * Utiliza a chave secreta (Simétrica) e o algoritmo HMAC-SHA256 (HS256).
     * Se a assinatura do token não bater com esta chave, a requisição é rejeitada (401).
     * </p>
     *
     * @return Uma instância de {@link JwtDecoder} configurada com a chave secreta.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKeySpec)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Configura o conversor que extrai as permissões (roles) de dentro do token JWT.
     * <p>
     * O Spring Security, por padrão, procura por "scope" ou "scp". Aqui nós customizamos
     * para procurar pela claim "roles" e adicionar o prefixo "ROLE_" para compatibilidade
     * com o método {@code .hasRole()}.
     * </p>
     *
     * @return O conversor configurado.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");

        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

}