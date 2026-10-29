package com.vellumhub.user_service.share.security.config;

import com.vellumhub.user_service.module.user.application.controller.UserController;
import com.vellumhub.user_service.module.user.domain.service.UserService;
import com.vellumhub.user_service.share.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = UserController.class, properties = {
        "JWT_JWKS_URI=http://127.0.0.1:1/oauth2/jwks",
        "VELLUM_DOCS_ENABLED=false"
})
@Import(SecurityConfig.class)
class UserSecurityBoundaryTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserService userService;

    @MockitoBean
    AuthenticationService authenticationService;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void protectedProfilesRequireAuthIssuedBearer() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid access token\"}"));
    }

    @Test
    void legacyAuthenticationRoutesAreNotPublicOrImplemented() throws Exception {
        mockMvc.perform(post("/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"legacy@example.invalid\",\"password\":\"irrelevant\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/login").with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/auth/google").with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedAccessDeniedUsesTheJsonAuthorizationContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"code\":\"FORBIDDEN\",\"message\":\"Insufficient permission\"}"));
    }
}
