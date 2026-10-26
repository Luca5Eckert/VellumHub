package com.vellumhub.auth;

import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import com.vellumhub.auth.identity.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** Signed, controlled upstream OIDC over HTTP + real Auth/Flyway/PostgreSQL. No Google credentials. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=local-poc,google-oidc", "vellum.poc.fixture.enabled=false",
        "vellum.identity.registration-enabled=true", "vellum.identity.shared-secret=test-only-internal-key-at-least-32-chars",
        "vellum.identity.reconcile-delay-ms=3600000"})
class FederatedOidcLifecycleIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Path keys = AuthPersistenceIntegrationTest.makeKeys();
    static final RSAKey upstreamKey = key();
    static final HttpServer upstream = provider();
    static volatile String upstreamNonce;
    static volatile String subject;
    static volatile String email;
    static volatile String variant;
    static volatile String lastGoogleToken;
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityRepository identities;
    @Autowired GoogleLinkChallengeRepository challenges;
    @Autowired JdbcIndexedSessionRepository sessions;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", postgres::getJdbcUrl);
        p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
        p.add("vellum.auth.keys.private-key-path", () -> keys.resolve("private.pem").toString());
        p.add("vellum.auth.keys.public-key-path", () -> keys.resolve("public.pem").toString());
        p.add("vellum.auth.keys.kid", () -> "local-test-key");
        p.add("vellum.identity.user-service-url", () -> providerUrl());
        p.add("spring.security.oauth2.client.registration.google.client-id", () -> "controlled-client");
        p.add("spring.security.oauth2.client.registration.google.client-secret", () -> "controlled-secret");
        for (String endpoint : List.of("authorization", "token", "user-info", "jwk-set")) {
            String path = switch (endpoint) { case "authorization" -> "/authorize"; case "user-info" -> "/userinfo";
                case "jwk-set" -> "/jwks"; default -> "/token"; };
            p.add("spring.security.oauth2.client.provider.google." + endpoint + "-uri", () -> providerUrl() + path);
        }
    }

    @BeforeEach void resetProvider() {
        subject = "subject-" + UUID.randomUUID();
        email = subject + "@example.invalid";
        variant = "valid";
        jdbc.update("DELETE FROM auth_attempt_window");
    }
    @AfterAll static void stopProvider() { upstream.stop(0); }

    @Test void googleCodeExchangeSurvivesJdbcSerializationAndRetainsCanonicalSubject() throws Exception {
        Browser b = new Browser();
        Map<String, Object> tokens = googleTokens(b);
        assertThat(tokens).doesNotContainKey("refresh_token");
        var claims = SignedJWT.parse((String) tokens.get("access_token")).getJWTClaimsSet();
        UUID id = UUID.fromString(claims.getSubject());
        assertThat(id.version()).isEqualTo(7);
        assertThat(claims.getIssuer()).isEqualTo("http://127.0.0.1:9000");
        assertThat(claims.getSubject()).isNotEqualTo(subject);
        assertThat(identities.findById(id).orElseThrow().active()).isTrue();
        assertThat(identities.findById(id).orElseThrow().passwordHash()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_external_identity WHERE user_id=?", Integer.class, id)).isEqualTo(1);
        // A second independent browser must resolve exactly the same identity.
        var repeated = googleTokens(new Browser());
        assertThat(SignedJWT.parse((String) repeated.get("access_token")).getJWTClaimsSet().getSubject()).isEqualTo(id.toString());
        // Google and local OIDC ID tokens must not pass the API's JWT access-token contract.
        assertThat(b.get("/poc/api/me", lastGoogleToken).statusCode()).isEqualTo(401);
        assertThat(b.get("/poc/api/me", (String) tokens.get("id_token")).statusCode()).isEqualTo(401);
        assertThat(b.get("/poc/api/me", (String) tokens.get("access_token")).statusCode()).isEqualTo(200);
    }

    @Test void rejectsBadNonceAudienceIssuerUnverifiedEmailAndStateBeforeCreatingIdentity() throws Exception {
        for (String invalid : List.of("nonce", "audience", "issuer", "email", "signature", "state")) {
            variant = invalid;
            Browser b = new Browser();
            var callback = googleCallback(b);
            assertThat(callback.statusCode()).isBetween(300, 399);
            assertThat(callback.headers().firstValue("location").orElse("")).contains("error");
            assertThat(identities.findByLogin(email)).isEmpty();
        }
    }

    @Test void logoutRequiresCsrfEndsAsSessionAndDoesNotRevokePreviouslyIssuedJwt() throws Exception {
        Browser b = new Browser();
        var tokens = googleTokens(b);
        assertThat(b.post("/logout", "").statusCode()).isEqualTo(403);
        var confirmation = b.get("/logout");
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(confirmation.body());
        assertThat(matcher.find()).isTrue();
        assertThat(b.post("/logout", "_csrf=" + enc(matcher.group(1))).statusCode()).isEqualTo(302);
        assertThat(b.get(authorize()).headers().firstValue("location").orElse("")).contains("/login");
        assertThat(b.get("/poc/api/me", (String) tokens.get("access_token")).statusCode()).isEqualTo(200);
    }

    @Test void disabledIdentityCannotReuseAsSessionOrRedeemOutstandingCode() throws Exception {
        Browser b = new Browser();
        var tokens = googleTokens(b);
        UUID id = UUID.fromString(SignedJWT.parse((String) tokens.get("access_token")).getJWTClaimsSet().getSubject());
        String redirect = b.get(authorize()).headers().firstValue("location").orElseThrow();
        String code = params(URI.create(redirect).getRawQuery()).get("code");
        jdbc.update("UPDATE auth_identity SET active=false WHERE id=?", id);
        assertThat(exchange(b, code).statusCode()).isEqualTo(400);
        assertThat(b.get(authorize()).headers().firstValue("location").orElse("")).contains("/login");
    }

    @Test void challengeHasExactlyOneConsumerAcrossIndependentRepositoryInstances() throws Exception {
        UUID id = UUID.randomUUID();
        identities.createIfAbsent(id, id + "@example.invalid", "{noop}test-only");
        var other = new GoogleLinkChallengeRepository(jdbc);
        challenges.issue("session-" + id, id, "one-time-nonce", Instant.now().plusSeconds(60));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return challenges.consume("session-" + id, id); });
            var c = pool.submit(() -> { start.await(); return other.consume("session-" + id, id); });
            start.countDown();
            assertThat(Arrays.asList(a.get(), c.get())).containsExactlyInAnyOrder("one-time-nonce", null);
        }
        challenges.issue("expired-" + id, id, "expired", Instant.now().minusSeconds(1));
        assertThat(other.consume("expired-" + id, id)).isNull();
    }

    @Test void federatedSecurityContextSurvivesIndependentJdbcSessionRepositoryAndDeletion() {
        var token = new OidcIdToken("test-signed-elsewhere", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("iss", "https://accounts.google.com", "sub", subject));
        var principal = new com.vellumhub.auth.config.GoogleFederationConfig.CanonicalOidcUser(
                UUID.randomUUID().toString(), new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), token));
        org.springframework.session.Session session = sessions.createSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google")));
        ((org.springframework.session.SessionRepository) sessions).save(session);
        var replica = new JdbcIndexedSessionRepository(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())));
        org.springframework.session.Session restored = replica.findById(session.getId());
        assertThat(restored).isNotNull();
        SecurityContextImpl context = restored.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getName()).isEqualTo(principal.getName());
        replica.deleteById(session.getId());
        assertThat(sessions.findById(session.getId())).isNull();
    }

    private Map<String, Object> googleTokens(Browser b) throws Exception {
        b.get(authorize());
        var callback = googleCallback(b);
        String savedRequest = callback.headers().firstValue("location").orElseThrow();
        var authorized = b.get(savedRequest);
        String redirect = authorized.headers().firstValue("location").orElseThrow();
        String code = params(URI.create(redirect).getRawQuery()).get("code");
        assertThat(code).as("authorization code after Google callback").isNotBlank();
        var result = exchange(b, code);
        assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
        return json(result.body());
    }
    private HttpResponse<String> googleCallback(Browser b) throws Exception {
        var start = b.get("/oauth2/authorization/google");
        var query = params(URI.create(start.headers().firstValue("location").orElseThrow()).getRawQuery());
        upstreamNonce = query.get("nonce");
        String state = "state".equals(variant) ? "invalid-state" : query.get("state");
        return b.get("/login/oauth2/code/google?code=test-code&state=" + enc(state));
    }
    private static final String VERIFIER = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-._~";
    private String authorize() throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        return "/oauth2/authorize?response_type=code&client_id=vellumhub-spa-poc&scope=openid%20profile"
                + "&redirect_uri=" + enc("http://127.0.0.1:9100/index.html")
                + "&state=local-state&nonce=local-nonce&code_challenge_method=S256&code_challenge=" + challenge;
    }
    private HttpResponse<String> exchange(Browser b, String code) throws Exception {
        return b.post("/oauth2/token", "grant_type=authorization_code&client_id=vellumhub-spa-poc&code=" + enc(code)
                + "&redirect_uri=" + enc("http://127.0.0.1:9100/index.html") + "&code_verifier=" + enc(VERIFIER));
    }
    private class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> get(String path) throws Exception { return get(path, null); }
        HttpResponse<String> get(String path, String bearer) throws Exception {
            var builder = HttpRequest.newBuilder(uri(path)).header("Accept", "text/html");
            if (bearer != null) { builder.header("Authorization", "Bearer " + bearer); }
            return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> post(String path, String body) throws Exception {
            return http.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        URI uri(String path) { return URI.create(path.startsWith("http") ? path : "http://127.0.0.1:" + port + path); }
    }
    private static HttpServer provider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try {
                    String path = exchange.getRequestURI().getPath();
                    String body;
                    if (path.equals("/jwks")) { body = new JWKSet(upstreamKey.toPublicJWK()).toString(); }
                    else if (path.equals("/token")) {
                        var claims = new JWTClaimsSet.Builder().issuer("issuer".equals(variant) ? "https://wrong.invalid" : "https://accounts.google.com")
                                .subject(subject).audience("audience".equals(variant) ? "wrong-client" : "controlled-client")
                                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300)))
                                .claim("nonce", "nonce".equals(variant) ? "wrong-nonce" : upstreamNonce)
                                .claim("email", email).claim("email_verified", !"email".equals(variant)).claim("name", "Test Google User").build();
                        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("upstream-key").build(), claims);
                        jwt.sign(new RSASSASigner("signature".equals(variant) ? key() : upstreamKey));
                        lastGoogleToken = jwt.serialize();
                        body = new tools.jackson.databind.json.JsonMapper().writeValueAsString(Map.of("access_token", "controlled-upstream-access",
                                "token_type", "Bearer", "expires_in", 300, "scope", "openid profile email", "id_token", lastGoogleToken));
                    } else if (path.equals("/userinfo")) {
                        body = new tools.jackson.databind.json.JsonMapper().writeValueAsString(Map.of("sub", subject, "email", email,
                                "email_verified", !"email".equals(variant), "name", "Test Google User"));
                    } else { body = "{}"; } // idempotent User profile command stub
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
                finally { exchange.close(); }
            });
            server.start();
            return server;
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private static RSAKey key() {
        try { return new RSAKeyGenerator(2048).keyID("upstream-key").generate(); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private static String providerUrl() { return "http://127.0.0.1:" + upstream.getAddress().getPort(); }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static Map<String, String> params(String query) {
        var result = new HashMap<String, String>();
        for (String pair : query.split("&")) {
            var parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> json(String value) {
        return new tools.jackson.databind.json.JsonMapper().readValue(value, Map.class);
    }
}
