package com.vellumhub.engagement_service.module.interaction.application.presentation.controller;

import com.vellumhub.engagement_service.module.interaction.application.command.CreateInteractionCommand;
import com.vellumhub.engagement_service.module.interaction.application.presentation.dto.request.CreateInteractionRequest;
import com.vellumhub.engagement_service.module.interaction.application.use_case.CreateInteractionUseCase;
import com.vellumhub.engagement_service.module.interaction.application.use_case.GetAllInteractionByUserUseCase;
import com.vellumhub.engagement_service.module.interaction.application.use_case.GetInteractionUseCase;
import com.vellumhub.engagement_service.module.interaction.application.use_case.UpdateInteractionUseCase;
import com.vellumhub.engagement_service.share.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public InteractionController(CreateInteractionUseCase createInteractionUseCase, UpdateInteractionUseCase updateInteractionUseCase, GetAllInteractionByUserUseCase getAllInteractionByUserUseCase, GetInteractionUseCase getInteractionUseCase, AuthenticationService authenticationService) {
        this.createInteractionUseCase = createInteractionUseCase;
        this.updateInteractionUseCase = updateInteractionUseCase;
        this.getAllInteractionByUserUseCase = getAllInteractionByUserUseCase;
        this.getInteractionUseCase = getInteractionUseCase;
        this.authenticationService = authenticationService;
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



}
