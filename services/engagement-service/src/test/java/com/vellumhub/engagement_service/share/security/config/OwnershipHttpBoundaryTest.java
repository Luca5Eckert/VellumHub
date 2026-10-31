package com.vellumhub.engagement_service.share.security.config;

import com.vellumhub.engagement_service.module.rating.application.controller.RatingController;
import com.vellumhub.engagement_service.module.rating.application.handler.*;
import com.vellumhub.engagement_service.module.reaction.application.use_case.*;
import com.vellumhub.engagement_service.module.reaction.domain.exception.ReactionNotFoundException;
import com.vellumhub.engagement_service.module.reaction.presentation.controller.ReactionController;
import com.vellumhub.engagement_service.module.reaction.presentation.mapper.ReactionMapper;
import com.vellumhub.engagement_service.share.exception.GlobalExceptionHandler;
import com.vellumhub.engagement_service.share.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = {RatingController.class, ReactionController.class}, properties = {
        "JWT_JWKS_URI=http://127.0.0.1:1/oauth2/jwks",
        "VELLUM_DOCS_ENABLED=false"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class OwnershipHttpBoundaryTest {
    @Autowired MockMvc mvc;

    @MockitoBean AuthenticationService authenticationService;
    @MockitoBean CreateRatingHandler createRatingHandler;
    @MockitoBean GetUserRatingHandler getUserRatingHandler;
    @MockitoBean UpdateRatingHandler updateRatingHandler;
    @MockitoBean DeleteRatingHandler deleteRatingHandler;
    @MockitoBean GetAllRatingByBookIdHandler getAllRatingByBookIdHandler;
    @MockitoBean CreateReactionUseCase createReactionUseCase;
    @MockitoBean UpdateReactionUseCase updateReactionUseCase;
    @MockitoBean GetAllReactionByUserUseCase getAllReactionByUserUseCase;
    @MockitoBean GetReactionUseCase getReactionUseCase;
    @MockitoBean ReactionMapper reactionMapper;

    private org.springframework.test.web.servlet.request.RequestPostProcessor human(UUID id, String scope) {
        return jwt().jwt(token -> token.subject(id.toString()).claim("grant_type", "authorization_code"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("SCOPE_" + scope));
    }

    @Test
    void otherUsersReactionIsHiddenUnderSame404AsMissingRecord() throws Exception {
        UUID subject = UUID.randomUUID();
        when(authenticationService.getAuthenticatedUserId()).thenReturn(subject);
        when(getReactionUseCase.execute(any())).thenThrow(new ReactionNotFoundException());
        mvc.perform(get("/reactions/123").with(human(subject, "engagement:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
        verify(getReactionUseCase).execute(argThat(query -> query.interactionId().equals(123L)
                && query.requesterId().equals(subject)));
    }

    @Test
    void userRatingsAreScopedAndPublicBookRatingsHaveDistinctRoute() throws Exception {
        UUID subject = UUID.randomUUID(), requested = UUID.randomUUID(), book = UUID.randomUUID();
        when(authenticationService.getAuthenticatedUserId()).thenReturn(subject);
        when(getUserRatingHandler.handle(eq(requested), eq(subject), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(getAllRatingByBookIdHandler.handle(eq(book), anyInt(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/rating/{userId}", requested).with(human(subject, "engagement:read")))
                .andExpect(status().isOk());
        verify(getUserRatingHandler).handle(eq(requested), eq(subject),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(10));
        mvc.perform(get("/rating/books/{bookId}", book).with(human(subject, "engagement:read")))
                .andExpect(status().isOk());
        verify(getAllRatingByBookIdHandler).handle(book, 0, 10);
    }

    @Test
    void anonymousAndWrongScopeCannotReadPrivateReaction() throws Exception {
        mvc.perform(get("/reactions/123")).andExpect(status().isUnauthorized());
        mvc.perform(get("/reactions/123").with(human(UUID.randomUUID(), "engagement:write")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(getReactionUseCase);
    }
}
