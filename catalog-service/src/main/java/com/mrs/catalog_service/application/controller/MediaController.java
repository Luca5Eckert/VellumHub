package com.mrs.catalog_service.application.controller;

import com.mrs.catalog_service.application.dto.CreateMediaRequest;
import com.mrs.catalog_service.application.dto.GetMediaResponse;
import com.mrs.catalog_service.application.dto.UpdateMediaRequest; // Import adicionado
import com.mrs.catalog_service.domain.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/media")
@Tag(name = "Media", description = "Endpoints para gerenciamento de catálogo de mídia")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Criar nova mídia", description = "Cria uma nova entrada de mídia no catálogo. Requer role ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Mídia criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador", content = @Content)
    })
    public ResponseEntity<Void> create(@RequestBody @Valid CreateMediaRequest createMediaRequest){
        mediaService.create(createMediaRequest);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deletar mídia", description = "Remove uma mídia do catálogo. Requer role ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Mídia deletada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mídia não encontrada", content = @Content)
    })
    public ResponseEntity<Void> delete(@Parameter(description = "ID da mídia") @PathVariable UUID id){
        mediaService.delete(id);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar mídia por ID", description = "Retorna os detalhes de uma mídia específica")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mídia encontrada", 
                    content = @Content(schema = @Schema(implementation = GetMediaResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mídia não encontrada", content = @Content)
    })
    public ResponseEntity<GetMediaResponse> getById(@Parameter(description = "ID da mídia") @PathVariable UUID id) {
        GetMediaResponse mediaResponse = mediaService.get(id);

        return ResponseEntity.ok(mediaResponse);
    }

    @GetMapping
    @Operation(summary = "Listar todas as mídias", description = "Retorna uma lista paginada de todas as mídias no catálogo")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de mídias retornada com sucesso",
                    content = @Content(schema = @Schema(implementation = GetMediaResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    public ResponseEntity<List<GetMediaResponse>> getAll(
            @Parameter(description = "Número da página") @RequestParam(defaultValue = "0") int pageNumber,
            @Parameter(description = "Tamanho da página") @RequestParam(defaultValue = "10") int pageSize
    ) {
        List<GetMediaResponse> mediaResponseList = mediaService.getAll(pageNumber, pageSize);

        return ResponseEntity.ok(mediaResponseList);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualizar mídia", description = "Atualiza os dados de uma mídia existente. Requer role ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mídia atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mídia não encontrada", content = @Content)
    })
    public ResponseEntity<Void> update(
            @Parameter(description = "ID da mídia") @PathVariable UUID id,
            @RequestBody @Valid UpdateMediaRequest updateMediaRequest
    ) {
        mediaService.update(id, updateMediaRequest);

        return ResponseEntity.ok().build();
    }
}