package com.mrs.recommendation_service.module.book_feature.application.controller;

import com.mrs.recommendation_service.share.provider.UserAuthenticationProvider;
import com.mrs.recommendation_service.module.book_feature.application.dto.BookFeatureResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Recommendations", description = "Endpoints para obter recomendações personalizadas de mídia")
public class RecommendationController {

    private final UserAuthenticationProvider userAuthenticationProvider;

    public RecommendationController(
            UserAuthenticationProvider userAuthenticationProvider) {
        this.userAuthenticationProvider = userAuthenticationProvider;
    }

    /**
     * Retorna as recomendações para o usuário autenticado.
     *
     * @return Lista de recomendações do usuário
     */
    @GetMapping
    @Operation(summary = "Obter recomendações", description = "Retorna recomendações personalizadas para o usuário autenticado")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recomendações retornadas com sucesso",
                    content = @Content(schema = @Schema(implementation = BookFeatureResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    public ResponseEntity<List<BookFeatureResponse>> getRecommendations(int limit, int offset) {
        var userId = userAuthenticationProvider.getUserId();

        // var recommendations = recommendationService.get(userId, limit, offset);

        return ResponseEntity.ok(null);
    }

}