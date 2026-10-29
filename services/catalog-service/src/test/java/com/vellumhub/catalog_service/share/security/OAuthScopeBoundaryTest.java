package com.vellumhub.catalog_service.share.security;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the real filter chain independently of domain handlers and persistence. */
@SpringJUnitWebConfig(OAuthScopeBoundaryTest.TestConfig.class)
class OAuthScopeBoundaryTest {
    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, Probe.class})
    static class TestConfig { }

    @RestController
    static class Probe {
        @RequestMapping("/**")
        ResponseEntity<Void> handle() { return ResponseEntity.noContent().build(); }
    }

    @Autowired WebApplicationContext context;

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor human(String role, String scope, String grant) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject("018d6373-864a-7000-8000-000000000001").claim("grant_type", grant))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role),
                        new SimpleGrantedAuthority("SCOPE_" + scope));
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/books,catalog:read,false",
            "GET,/books/123,catalog:read,false",
            "GET,/books/123/cover,catalog:read,false",
            "POST,/books/bulk,catalog:read,false",
            "POST,/books/covers/bulk,catalog:read,false",
            "POST,/books,catalog:write,true",
            "POST,/books/isbn,catalog:write,true",
            "POST,/books/123/cover,catalog:write,true",
            "PUT,/books/123,catalog:write,true",
            "DELETE,/books/123,catalog:write,true",
            "POST,/book-requests,catalog:requests:write,false",
            "POST,/book-requests/123/approve,catalog:write,true",
            "GET,/book-requests,catalog:requests:read,true",
            "POST,/book-progress/123/status,catalog:personal:write,false",
            "PUT,/book-progress/123/progress,catalog:personal:write,false",
            "DELETE,/book-progress/123,catalog:personal:write,false",
            "GET,/book-progress/reading-list,catalog:personal:read,false",
            "POST,/book-list,catalog:personal:write,false",
            "PUT,/book-list/123,catalog:personal:write,false",
            "DELETE,/book-list/123,catalog:personal:write,false",
            "GET,/book-list,catalog:personal:read,false",
            "GET,/book-list/123,catalog:personal:read,false",
            "POST,/book-list/123/books/456,catalog:personal:write,false",
            "DELETE,/book-list/123/books/456,catalog:personal:write,false",
            "POST,/book-list/membership/123,catalog:personal:write,false",
            "PUT,/book-list/membership/123/role,catalog:personal:write,false",
            "DELETE,/book-list/membership/123/456,catalog:personal:write,false",
            "GET,/book-list/membership,catalog:personal:read,false",
            "GET,/book-list/membership/123,catalog:personal:read,false"
    })
    void enforcesHumanRoleAndIndependentScopeOnEveryOperation(String method, String path, String scope, boolean admin) throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        HttpMethod verb = HttpMethod.valueOf(method);
        mvc.perform(request(verb, path)).andExpect(status().isUnauthorized());
        mvc.perform(request(verb, path).with(human("USER", scope, "authorization_code")))
                .andExpect(admin ? status().isForbidden() : status().isNoContent());
        mvc.perform(request(verb, path).with(human("ADMIN", scope, "authorization_code")))
                .andExpect(status().isNoContent());
        for (String role : List.of("USER", "ADMIN")) {
            mvc.perform(request(verb, path).with(SecurityMockMvcRequestPostProcessors.jwt()
                            .jwt(jwt -> jwt.claim("grant_type", "authorization_code"))
                            .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isForbidden());
            mvc.perform(request(verb, path).with(human(role, scope, "unspecified")))
                    .andExpect(status().isForbidden());
            mvc.perform(request(verb, path).with(human(role, "unrelated:scope", "authorization_code")))
                    .andExpect(status().isForbidden());
            mvc.perform(request(verb, path).with(human(role, scope, "client_credentials")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(request(verb, path).with(SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownRoutesAreDeniedEvenToAnAdministrator() throws Exception {
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(request(HttpMethod.GET, "/not-a-domain-route")
                        .with(human("ADMIN", "user:read", "authorization_code")))
                .andExpect(status().isForbidden());
    }

    @Test
    void everyDomainOperationDocumentsItsRequiredOAuthScope() {
        for (Class<?> controller : List.of(com.vellumhub.catalog_service.module.book_list.presentation.controller.BookListController.class,
                com.vellumhub.catalog_service.module.book_list.presentation.controller.BookListMembershipController.class,
                com.vellumhub.catalog_service.module.book_progress.application.controller.BookProgressController.class,
                com.vellumhub.catalog_service.module.book.presentation.controller.BookController.class,
                com.vellumhub.catalog_service.module.book_request.application.controller.BookRequestController.class)) {
            for (var method : controller.getDeclaredMethods()) {
                if (java.util.Arrays.stream(method.getAnnotations()).noneMatch(annotation ->
                        annotation.annotationType().isAnnotationPresent(org.springframework.web.bind.annotation.RequestMapping.class))) {
                    continue;
                }
                var security = method.getAnnotation(io.swagger.v3.oas.annotations.security.SecurityRequirement.class);
                org.assertj.core.api.Assertions.assertThat(security).as(method.toString()).isNotNull();
                org.assertj.core.api.Assertions.assertThat(security.name()).isEqualTo("oauth2");
                org.assertj.core.api.Assertions.assertThat(security.scopes()).hasSize(1);
                org.assertj.core.api.Assertions.assertThat(com.vellumhub.security.jwt.OAuthScopes.API)
                        .contains(security.scopes()[0]);
            }
        }
    }
}
