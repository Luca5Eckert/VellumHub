package com.mrs.engagement_service.application.controller;

import com.mrs.engagement_service.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.application.dto.InteractionCreateRequest;
import com.mrs.engagement_service.application.dto.InteractionGetResponse;
import com.mrs.engagement_service.domain.model.InteractionType;
import com.mrs.engagement_service.domain.service.EngagementService;
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
@RequestMapping("/engagement")
@Tag(name = "Engagement", description = "Endpoints para gerenciamento de interações de usuários com mídia")
public class EngagementController {

    private final EngagementService engagementService;

    public EngagementController(EngagementService engagementService) {
        this.engagementService = engagementService;
    }

    @PostMapping
    @Operation(summary = "Registrar interação", description = "Registra uma nova interação do usuário com uma mídia (like, view, rating)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Interação registrada com sucesso",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    public ResponseEntity<String> create(@RequestBody @Valid InteractionCreateRequest engagement) {
        engagementService.create(engagement);
        return ResponseEntity.status(HttpStatus.CREATED).body("Engagement registered with success");
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Buscar interações do usuário", description = "Retorna todas as interações de um usuário específico com filtros opcionais")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de interações retornada com sucesso",
                    content = @Content(schema = @Schema(implementation = InteractionGetResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    public ResponseEntity<List<InteractionGetResponse>> findAllOfUser(
            @Parameter(description = "ID do usuário") @PathVariable UUID userId,
            @Parameter(description = "Filtrar por tipo de interação") @RequestParam(required = false) InteractionType type,
            @Parameter(description = "Data inicial (ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Data final (ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Número da página") @RequestParam(defaultValue = "0") int pageNumber,
            @Parameter(description = "Tamanho da página") @RequestParam(defaultValue = "10") int pageSize
    ) {
        List<InteractionGetResponse> response = engagementService.findAllOfUser(
                userId, type, from, to, pageNumber, pageSize
        );
        return ResponseEntity.ok(response);
    }


    @GetMapping("/media/{mediaId}/stats")
    @Operation(summary = "Obter estatísticas de mídia", description = "Retorna as estatísticas de engajamento de uma mídia específica")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estatísticas retornadas com sucesso",
                    content = @Content(schema = @Schema(implementation = GetMediaStatusResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mídia não encontrada", content = @Content)
    })
    public ResponseEntity<GetMediaStatusResponse> getMediaStatus(@Parameter(description = "ID da mídia") @PathVariable UUID mediaId) {
        GetMediaStatusResponse response = engagementService.getMediaStatus(mediaId);

        return ResponseEntity.ok(response);
    }

}