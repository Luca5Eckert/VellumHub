package com.vellumhub.auth.identity;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Explicitly opt-in, development-only fixture. Never seeds a shared/default environment.
 * Does not print passwords and does not alter existing credentials on restart.
 */
@Component
@ConditionalOnProperty(prefix = "vellum.poc.fixture", name = "enabled", havingValue = "true")
public class PocIdentityFixture implements ApplicationRunner {
    private final IdentityRepository identities;
    private final UuidV7 ids;
    private final PasswordEncoder passwords;
    private final String login;
    private final String password;

    public PocIdentityFixture(IdentityRepository identities, UuidV7 ids, PasswordEncoder passwords,
                              @Value("${vellum.poc.fixture.login:}") String login,
                              @Value("${vellum.poc.fixture.password:}") String password) {
        this.identities = identities;
        this.ids = ids;
        this.passwords = passwords;
        this.login = login;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (login.isBlank() || password.length() < 12) {
            throw new IllegalStateException("PoC fixture requires a login and password of at least 12 characters");
        }
        String normalized = login.strip().toLowerCase(Locale.ROOT);
        if (identities.findByLogin(normalized).isEmpty()) {
            identities.createIfAbsent(ids.next(), normalized, passwords.encode(password));
        }
    }
}
