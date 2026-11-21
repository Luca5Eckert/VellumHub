package com.vellumhub.user_service.controller;

import com.vellumhub.kafka.contracts.user.CatalogGenreNames;
import com.vellumhub.user_service.module.user_preference.application.command.CreateUserPreferenceCommand;
import com.vellumhub.user_service.module.user_preference.application.use_case.CreateUserPreferenceUseCase;
import com.vellumhub.user_service.module.user_preference.presentation.controller.UserPreferenceController;
import com.vellumhub.user_service.share.service.AuthenticationService;
import com.vellumhub.user_service.share.security.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserPreferenceController.class)
@Import({AuthenticationService.class, SecurityConfig.class})
class UserPreferenceControllerTest {
    private static final UUID SUBJECT = UUID.randomUUID();
    private static final String OWN = "/users/me/preferences";

    @Autowired MockMvc mvc;
    @MockitoBean CreateUserPreferenceUseCase preferences;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void readsCatalogGenresAndOwnPreferences() throws Exception {
        when(preferences.findGenres(SUBJECT)).thenReturn(List.of("Fantasy"));
        mvc.perform(get("/users/genres").with(human("user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genres.length()").value(CatalogGenreNames.ONBOARDING.size()));
        mvc.perform(get(OWN).with(human("user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genres[0]").value("Fantasy"));
    }

    @Test
    void replacesPreferencesForJwtSubjectOnly() throws Exception {
        mvc.perform(put(OWN).with(human("user:write")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"genres\":[\"Science Fiction\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genres[0]").value("Science Fiction"));
        verify(preferences).execute(new CreateUserPreferenceCommand(SUBJECT, List.of("Science Fiction"), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[\"unknown\"]", "[\"Fantasy\",\"Fantasy\"]", "[null]"})
    void rejectsInvalidSubmission(String genres) throws Exception {
        mvc.perform(put(OWN).with(human("user:write")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"genres\":" + genres + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(preferences);
    }

    @Test
    void deniesAnonymousWrongScopeAndAnotherUserPath() throws Exception {
        mvc.perform(get(OWN)).andExpect(status().isUnauthorized());
        mvc.perform(put(OWN).with(human("user:read"))).andExpect(status().isForbidden());
        mvc.perform(put("/users/{id}/preferences", UUID.randomUUID()).with(human("user:write")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(preferences);
    }

    private RequestPostProcessor human(String scope) {
        return jwt().jwt(token -> token.subject(SUBJECT.toString()).claim("grant_type", "authorization_code"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("SCOPE_" + scope));
    }
}
