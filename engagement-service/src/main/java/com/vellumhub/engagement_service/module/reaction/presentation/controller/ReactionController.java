package com.vellumhub.engagement_service.module.reaction.presentation.controller;

import com.vellumhub.engagement_service.module.reaction.application.command.CreateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.application.command.UpdateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.application.query.GetAllReactionByUserQuery;
import com.vellumhub.engagement_service.module.reaction.application.query.GetReactionQuery;
import com.vellumhub.engagement_service.module.reaction.application.use_case.CreateReactionUseCase;
import com.vellumhub.engagement_service.module.reaction.application.use_case.GetAllReactionByUserUseCase;
import com.vellumhub.engagement_service.module.reaction.application.use_case.GetReactionUseCase;
import com.vellumhub.engagement_service.module.reaction.application.use_case.UpdateReactionUseCase;
import com.vellumhub.engagement_service.module.reaction.presentation.dto.request.CreateReactionRequest;
import com.vellumhub.engagement_service.module.reaction.presentation.dto.request.UpdateReactionRequest;
import com.vellumhub.engagement_service.module.reaction.presentation.dto.response.ReactionResponse;
import com.vellumhub.engagement_service.module.reaction.presentation.mapper.ReactionMapper;
import com.vellumhub.engagement_service.share.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "Reaction Controller",
        description = "Controller for managing reactions between users and books."
)
@RestController
@RequestMapping("/reactions")
public class ReactionController {

    private final CreateReactionUseCase createReactionUseCase;
    private final UpdateReactionUseCase updateReactionUseCase;
    private final GetAllReactionByUserUseCase getAllReactionByUserUseCase;
    private final GetReactionUseCase getReactionUseCase;

    private final AuthenticationService authenticationService;
    private final ReactionMapper reactionMapper;

    public ReactionController(CreateReactionUseCase createReactionUseCase, UpdateReactionUseCase updateReactionUseCase, GetAllReactionByUserUseCase getAllReactionByUserUseCase, GetReactionUseCase getReactionUseCase, AuthenticationService authenticationService, ReactionMapper reactionMapper) {
        this.createReactionUseCase = createReactionUseCase;
        this.updateReactionUseCase = updateReactionUseCase;
        this.getAllReactionByUserUseCase = getAllReactionByUserUseCase;
        this.getReactionUseCase = getReactionUseCase;
        this.authenticationService = authenticationService;
        this.reactionMapper = reactionMapper;
    }

    @Operation(
            summary = "Create Reaction",
            description = "Creates a new reaction between the authenticated user and a book."
    )
    @PostMapping
    public ResponseEntity<Void> create(
            @RequestBody @Valid CreateReactionRequest request
    ) {
        var userId = authenticationService.getAuthenticatedUserId();

        var command = CreateReactionCommand.of(userId, request.bookId(), request.typeReaction());
        createReactionUseCase.execute(command);

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Update Reaction",
            description = "Updates an existing reaction for the authenticated user."
    )
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable Long id,
            @RequestBody @Valid UpdateReactionRequest request
    ) {
        var userId = authenticationService.getAuthenticatedUserId();

        var command = UpdateReactionCommand.of(userId, id, request.typeReaction());
        updateReactionUseCase.execute(command);

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Get Reaction by ID",
            description = "Retrieves details of a specific reaction."
    )
    @GetMapping("/{id}")
    public ResponseEntity<ReactionResponse> getById(
            @PathVariable Long id
    ) {
        var query = GetReactionQuery.of(id);

        var reaction = getReactionUseCase.execute(query);

        return ResponseEntity.ok(
                reactionMapper.toResponse(reaction)
        );
    }

    @Operation(
            summary = "Get All User Reactions",
            description = "Retrieves all reactions belonging to the authenticated user."
    )
    @GetMapping
    public ResponseEntity<List<ReactionResponse>> getAllByUser() {
        var userId = authenticationService.getAuthenticatedUserId();

        var query = GetAllReactionByUserQuery.of(userId);

        var reactions = getAllReactionByUserUseCase.execute(query);
        var response = reactions.stream()
                .map(reactionMapper::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }


}
