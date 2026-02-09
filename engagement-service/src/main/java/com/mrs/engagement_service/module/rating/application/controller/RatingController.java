package com.mrs.engagement_service.module.rating.application.controller;

import com.mrs.engagement_service.module.rating.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.module.rating.application.dto.RatingCreateRequest;
import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.domain.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/rating")
@Tag(name = "Rating", description = "Endpoints para gerenciamento de ratings de usuários de livros")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping
    @Operation(summary = "Registrar rating", description = "Registra um novo rating do usuário com uma mídia (0-5 estrelas + review)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Rating registrado com sucesso",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    public ResponseEntity<String> create(@RequestBody @Valid RatingCreateRequest engagement) {
        ratingService.create(engagement);
        return ResponseEntity.status(HttpStatus.CREATED).body("Rating registered with success");
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Buscar ratings do usuário", description = "Retorna todos os ratings de um usuário específico com filtros opcionais")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de ratings retornada com sucesso",
                    content = @Content(schema = @Schema(implementation = RatingGetResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    public ResponseEntity<List<RatingGetResponse>> findAllOfUser(
            @Parameter(description = "ID do usuário") @PathVariable UUID userId,
            @Parameter(description = "Filtrar por estrelas mínimas") @RequestParam(required = false) Integer minStars,
            @Parameter(description = "Filtrar por estrelas máximas") @RequestParam(required = false) Integer maxStars,
            @Parameter(description = "Data inicial (ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Data final (ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Número da página") @RequestParam(defaultValue = "0") int pageNumber,
            @Parameter(description = "Tamanho da página") @RequestParam(defaultValue = "10") int pageSize
    ) {
        List<RatingGetResponse> response = ratingService.findAllOfUser(
                userId, minStars, maxStars, from, to, pageNumber, pageSize
        );
        return ResponseEntity.ok(response);
    }


    @GetMapping("/media/{mediaId}/stats")
    @Operation(summary = "Obter estatísticas de mídia", description = "Retorna as estatísticas de ratings de uma mídia específica")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estatísticas retornadas com sucesso",
                    content = @Content(schema = @Schema(implementation = GetMediaStatusResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mídia não encontrada", content = @Content)
    })
    public ResponseEntity<GetMediaStatusResponse> getMediaStatus(@Parameter(description = "ID da mídia") @PathVariable UUID mediaId) {
        GetMediaStatusResponse response = ratingService.getMediaStatus(mediaId);

        return ResponseEntity.ok(response);
    }

}