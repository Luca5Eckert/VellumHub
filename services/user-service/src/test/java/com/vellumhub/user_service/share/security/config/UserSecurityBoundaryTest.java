package com.vellumhub.user_service.share.security.config;

import com.vellumhub.user_service.module.auth.application.controller.AuthController;
import com.vellumhub.user_service.module.auth.domain.service.AuthService;
import com.vellumhub.user_service.module.user.domain.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AuthController.class, properties = {
        "jwt.public-key-location=classpath:security/test-public.pem",
        "VELLUM_DOCS_ENABLED=false"
})
@Import(SecurityConfig.class)
class UserSecurityBoundaryTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void googleLoginIsPublicWithoutOpeningTheEntireAuthPath() throws Exception {
        when(authService.login(any(com.vellumhub.user_service.module.auth.application.dto.LoginExternalRequest.class)))
                .thenReturn("test-jwt");
        mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalToken\":\"provider-fixture\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("test-jwt"));
        mockMvc.perform(get("/auth/google")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/not-public")).andExpect(status().isUnauthorized());
    }

    @Test
    void existingLocalLoginRemainsPublic() throws Exception {
        when(authService.login(any(com.vellumhub.user_service.module.auth.application.dto.LoginUserRequest.class)))
                .thenReturn("test-jwt");
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedUsersRequireAuthentication() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void bindingEndpointsRequireJwtAndForwardItsSubjectUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        String linkPayload = """
                {"externalToken":"verified-id-token","currentPassword":"password"}
                """;
        String unlinkPayload = """
                {"currentPassword":"password"}
                """;
        mockMvc.perform(post("/auth/google/link").contentType(MediaType.APPLICATION_JSON)
                .content(linkPayload)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/auth/google/link").contentType(MediaType.APPLICATION_JSON)
                .content(unlinkPayload)).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/google/link").with(jwt().jwt(token ->
                    token.claim("user_id", userId.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(linkPayload))
                .andExpect(status().isNoContent());
        verify(authService).linkGoogle(userId, "verified-id-token", "password");
        mockMvc.perform(delete("/auth/google/link").with(jwt().jwt(token ->
                    token.claim("user_id", userId.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(unlinkPayload))
                .andExpect(status().isNoContent());
        verify(authService).unlinkGoogle(userId, "password");
    }

}
