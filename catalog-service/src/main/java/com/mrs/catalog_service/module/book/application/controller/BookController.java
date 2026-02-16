package com.mrs.catalog_service.module.book.application.controller;

import com.mrs.catalog_service.module.book.application.dto.CreateBookRequest;
import com.mrs.catalog_service.module.book.application.dto.GetBookResponse;
import com.mrs.catalog_service.module.book.application.dto.Recommendation;
import com.mrs.catalog_service.module.book.application.dto.UpdateBookRequest; // Import adicionado
import com.mrs.catalog_service.module.book.domain.service.BookService;
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
@RequestMapping("/books")
@Tag(name = "Book", description = "Endpoints para gerenciamento de catálogo de livros")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Criar novo livro", description = "Cria um novo livro no catálogo. Requer role ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Livro criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador", content = @Content)
    })
    public ResponseEntity<Void> create(@RequestBody @Valid CreateBookRequest createMediaRequest){
        bookService.create(createMediaRequest);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deletar mídia", description = "Remove um livro do catálogo. Requer role ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Livro deletado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador", content = @Content),
            @ApiResponse(responseCode = "404", description = "Livro não encontrado", content = @Content)
    })
    public ResponseEntity<Void> delete(@Parameter(description = "ID do livro") @PathVariable UUID id){
        bookService.delete(id);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar mídia por ID", description = "Retorna os detalhes de um livro específico")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Livro encontrado", 
                    content = @Content(schema = @Schema(implementation = GetBookResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Livro não encontrado", content = @Content)
    })
    public ResponseEntity<GetBookResponse> getById(@Parameter(description = "ID do livro") @PathVariable UUID id) {
        GetBookResponse bookResponse = bookService.get(id);

        return ResponseEntity.ok(bookResponse);
    }

    @GetMapping
    @Operation(summary = "Listar todas as mídias", description = "Retorna uma lista paginada de todos os livros no catálogo")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de livros retornada com sucesso",
                    content = @Content(schema = @Schema(implementation = GetBookResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    public ResponseEntity<List<GetBookResponse>> getAll(
            @Parameter(description = "Número da página") @RequestParam(defaultValue = "0") int pageNumber,
            @Parameter(description = "Tamanho da página") @RequestParam(defaultValue = "10") int pageSize
    ) {
        List<GetBookResponse> mediaResponseList = bookService.getAll(pageNumber, pageSize);

        return ResponseEntity.ok(mediaResponseList);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualizar mídia", description = "Atualiza os dados de um livro existente. Requer role ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Livro atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão de administrador", content = @Content),
            @ApiResponse(responseCode = "404", description = "Livro não encontrado", content = @Content)
    })
    public ResponseEntity<Void> update(
            @Parameter(description = "ID do livro") @PathVariable UUID id,
            @RequestBody @Valid UpdateBookRequest updateMediaRequest
    ) {
        bookService.update(id, updateMediaRequest);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<Recommendation>> getByIds(List<UUID> bookIds) {
        List<Recommendation> recommendations = bookService.getByIds(bookIds);

        return ResponseEntity.ok(recommendations);
    }


}