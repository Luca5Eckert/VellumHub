package com.vellumhub.engagement_service.module.interaction.presentation.controller;

import com.vellumhub.engagement_service.module.interaction.application.command.CreateInteractionCommand;
import com.vellumhub.engagement_service.module.interaction.application.command.UpdateInteractionCommand;
import com.vellumhub.engagement_service.module.interaction.application.query.GetAllInteractionByUserQuery;
import com.vellumhub.engagement_service.module.interaction.application.query.GetInteractionQuery;
import com.vellumhub.engagement_service.module.interaction.application.use_case.CreateInteractionUseCase;
import com.vellumhub.engagement_service.module.interaction.application.use_case.GetAllInteractionByUserUseCase;
import com.vellumhub.engagement_service.module.interaction.application.use_case.GetInteractionUseCase;
import com.vellumhub.engagement_service.module.interaction.application.use_case.UpdateInteractionUseCase;
import com.vellumhub.engagement_service.module.interaction.presentation.dto.request.CreateInteractionRequest;
import com.vellumhub.engagement_service.module.interaction.presentation.dto.request.UpdateInteractionRequest;
import com.vellumhub.engagement_service.module.interaction.presentation.dto.response.InteractionResponse;
import com.vellumhub.engagement_service.module.interaction.presentation.mapper.InteractionMapper;
import com.vellumhub.engagement_service.share.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "Interaction Controller",
        description = "Controller for managing interactions between users and books."
)
@RestController
@RequestMapping("/interactions")
public class InteractionController {

    private final CreateInteractionUseCase createInteractionUseCase;
    private final UpdateInteractionUseCase updateInteractionUseCase;
    private final GetAllInteractionByUserUseCase getAllInteractionByUserUseCase;
    private final GetInteractionUseCase getInteractionUseCase;

    private final AuthenticationService authenticationService;
    private final InteractionMapper interactionMapper;

    public InteractionController(CreateInteractionUseCase createInteractionUseCase, UpdateInteractionUseCase updateInteractionUseCase, GetAllInteractionByUserUseCase getAllInteractionByUserUseCase, GetInteractionUseCase getInteractionUseCase, AuthenticationService authenticationService, InteractionMapper interactionMapper) {
        this.createInteractionUseCase = createInteractionUseCase;
        this.updateInteractionUseCase = updateInteractionUseCase;
        this.getAllInteractionByUserUseCase = getAllInteractionByUserUseCase;
        this.getInteractionUseCase = getInteractionUseCase;
        this.authenticationService = authenticationService;
        this.interactionMapper = interactionMapper;
    }

    @Operation(
            summary = "Create Interaction",
            description = "Creates a new interaction between the authenticated user and a book."
    )
    @PostMapping
    public ResponseEntity<Void> create(
            @RequestBody @Valid CreateInteractionRequest request
    ) {
        var userId = authenticationService.getAuthenticatedUserId();

        var command = CreateInteractionCommand.of(userId, request.bookId(), request.typeInteraction());
        createInteractionUseCase.execute(command);

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Update Interaction",
            description = "Updates an existing interaction for the authenticated user."
    )
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable Long id,
            @RequestBody @Valid UpdateInteractionRequest request
    ) {
        var userId = authenticationService.getAuthenticatedUserId();

        var command = UpdateInteractionCommand.of(userId, id, request.typeInteraction());
        updateInteractionUseCase.execute(command);

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Get Interaction by ID",
            description = "Retrieves details of a specific interaction."
    )
    @GetMapping("/{id}")
    public ResponseEntity<InteractionResponse> getById(
            @PathVariable Long id
    ) {
        var query = GetInteractionQuery.of(id);

        var interaction = getInteractionUseCase.execute(query);

        return ResponseEntity.ok(
                interactionMapper.toResponse(interaction)
        );
    }

    @Operation(
            summary = "Get All User Interactions",
            description = "Retrieves all interactions belonging to the authenticated user."
    )
    @GetMapping
    public ResponseEntity<List<InteractionResponse>> getAllByUser() {
        var userId = authenticationService.getAuthenticatedUserId();

        var query = GetAllInteractionByUserQuery.of(userId);

        var interactions = getAllInteractionByUserUseCase.execute(query);
        var response = interactions.stream()
                .map(interactionMapper::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }


}
