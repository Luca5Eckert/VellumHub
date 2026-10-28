package com.vellumhub.auth.config;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.vellumhub.security.jwt.JwtAccessToken;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfig {

    // Browser preflights may not match OAuth endpoint matchers (which can be POST-only).
    // Handle OPTIONS independently and allow ONLY the configured SPA origin.
    @Bean
    @Order(0)
    SecurityFilterChain browserPreflightChain(HttpSecurity http,
            @Value("${vellum.auth.allowed-origin}") String origin) throws Exception {
        http.securityMatcher(request -> "OPTIONS".equals(request.getMethod())
                && ("/oauth2/token".equals(request.getServletPath())
                    || "/oauth2/jwks".equals(request.getServletPath())
                    || request.getServletPath().startsWith("/.well-known/")
                    || request.getServletPath().startsWith("/poc/api/")
                    || "/v1/identity/register".equals(request.getServletPath())))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .cors(cors -> cors.configurationSource(corsSource(origin)))
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerChain(HttpSecurity http,
            com.vellumhub.auth.identity.IdentityRepository identities,
            @Value("${vellum.auth.allowed-origin}") String origin) throws Exception {
        http.oauth2AuthorizationServer(as -> {
            http.securityMatcher(as.getEndpointsMatcher());
            as.oidc(Customizer.withDefaults());
        });
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    htmlRequests()))
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                    PathPatternRequestMatcher.pathPattern("/oauth2/token")))
            .cors(cors -> cors.configurationSource(corsSource(origin)))
            .addFilterBefore(s256OnlyFilter(), SecurityContextHolderFilter.class)
            .addFilterAfter(new ActiveIdentitySessionFilter(identities), SecurityContextHolderFilter.class);
        return http.build();
    }

    private MediaTypeRequestMatcher htmlRequests() {
        var matcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        matcher.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        return matcher;
    }

    private OncePerRequestFilter s256OnlyFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                if ("/oauth2/authorize".equals(request.getServletPath())
                        && "code".equals(request.getParameter("response_type"))
                        && (!"S256".equals(request.getParameter("code_challenge_method"))
                        || request.getParameter("code_challenge") == null
                        || request.getParameter("code_challenge").isBlank())) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "PKCE S256 required");
                    return;
                }
                chain.doFilter(request, response);
            }
        };
    }

    @Bean
    @Order(2)
    SecurityFilterChain pocResourceServerChain(HttpSecurity http,
            @Value("${vellum.auth.issuer}") String issuer,
            @Value("${vellum.auth.api-audience:api://vellumhub}") String audience,
            @Value("${vellum.auth.allowed-origin}") String origin) throws Exception {
        var decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/oauth2/jwks")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtAccessToken.validator(issuer, audience, 30));
        http.securityMatcher("/poc/api/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder)))
            .cors(cors -> cors.configurationSource(corsSource(origin)))
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain applicationChain(HttpSecurity http,
            com.vellumhub.auth.identity.IdentityRepository identities,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            io.micrometer.core.instrument.MeterRegistry metrics,
            @Value("${vellum.auth.attempts-per-minute:20}") int attemptsPerMinute,
            @Value("${vellum.google.enabled:false}") boolean googleEnabled,
            ObjectProvider<OAuth2UserService<OidcUserRequest, OidcUser>> federatedUsers) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/v1/identity/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                .anyRequest().authenticated())
            // Registration is a stateless JSON operation and cannot obtain a browser CSRF token.
            // Scope this exemption to this path; retain CSRF for the interactive login flow.
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                    PathPatternRequestMatcher.pathPattern("/v1/identity/register")))
            .addFilterAfter(new ActiveIdentitySessionFilter(identities), SecurityContextHolderFilter.class)
            .addFilterBefore(new AuthenticationThrottleFilter(jdbc, metrics, attemptsPerMinute, java.time.Clock.systemUTC()),
                    org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter.class)
            .formLogin(Customizer.withDefaults())
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout")
                    .invalidateHttpSession(true).clearAuthentication(true)
                    .deleteCookies("SESSION", "JSESSIONID"));
        if (googleEnabled) {
            // Enable only with a configured provider and external credentials.
            http.oauth2Login(oauth -> oauth.userInfoEndpoint(userInfo ->
                    userInfo.oidcUserService(federatedUsers.getObject())));
        }
        return http.build();
    }

    private UrlBasedCorsConfigurationSource corsSource(String origin) {
        if (origin.isBlank() || "*".equals(origin)) {
            throw new IllegalArgumentException("One explicit SPA origin is required");
        }
        var policy = new CorsConfiguration();
        policy.setAllowedOrigins(List.of(origin));
        policy.setAllowedMethods(List.of("POST", "OPTIONS", "GET"));
        policy.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        policy.setMaxAge(300L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/oauth2/token", policy);
        source.registerCorsConfiguration("/.well-known/**", policy);
        source.registerCorsConfiguration("/oauth2/jwks", policy);
        source.registerCorsConfiguration("/poc/api/**", policy);
        source.registerCorsConfiguration("/v1/identity/register", policy);
        return source;
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(
            @Value("${vellum.auth.issuer}") String issuer) {
        if (issuer.isBlank()) {
            throw new IllegalStateException("A stable OAuth issuer is required");
        }
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    @org.springframework.context.annotation.Primary
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> keys) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(keys);
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> accessTokenClaims(
            com.vellumhub.auth.identity.IdentityRepository identities,
            @Value("${vellum.auth.api-audience:api://vellumhub}") String audience) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return; // OIDC ID tokens keep their own typ/aud semantics.
            }
            String subject = context.getPrincipal().getName();
            UUID.fromString(subject); // Fail closed if username/email leaks into the principal.
            var identity = identities.findById(UUID.fromString(subject))
                    .filter(com.vellumhub.auth.identity.IdentityRepository.Identity::active)
                    .orElseThrow(() -> new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_grant"));
            context.getJwsHeader().type("at+jwt");
            context.getClaims()
                    .subject(subject)
                    .audience(new ArrayList<>(List.of(audience)))
                    .id(UUID.randomUUID().toString())
                    .claim("client_id", context.getRegisteredClient().getClientId())
                    .claim("roles", new ArrayList<>(List.of(identity.role().name())));
        };
    }
}
