package com.mrs.recommendation_service.application.controller;

import com.mrs.recommendation_service.domain.model.Recommendation;
import com.mrs.recommendation_service.infrastructure.provider.UserAuthenticationProvider;
import com.mrs.recommendation_service.domain.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework. web.bind.annotation. GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework. web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final UserAuthenticationProvider userAuthenticationProvider;

    public RecommendationController(
            RecommendationService recommendationService,
            UserAuthenticationProvider userAuthenticationProvider) {
        this.recommendationService = recommendationService;
        this.userAuthenticationProvider = userAuthenticationProvider;
    }

    /**
     * Retorna as recomendações para o usuário autenticado.
     *
     * @return Lista de recomendações do usuário
     */
    @GetMapping
    public ResponseEntity<List<Recommendation>> getRecommendations() {
        var userId = userAuthenticationProvider.getUserId();

        var recommendations = recommendationService.get(userId);

        return ResponseEntity. ok(recommendations);
    }

}