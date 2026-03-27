package com.mrs.catalog_service.module.book_list.presentation.controller;

import com.mrs.catalog_service.module.book_list.application.command.member.AddMemberInBookListCommand;
import com.mrs.catalog_service.module.book_list.application.command.member.DeleteMemberInBookListCommand;
import com.mrs.catalog_service.module.book_list.application.command.member.UpdateMemberRoleCommand;
import com.mrs.catalog_service.module.book_list.application.query.member.GetAllMembershipOfBookListQuery;
import com.mrs.catalog_service.module.book_list.application.query.member.GetMembershipQuery;
import com.mrs.catalog_service.module.book_list.application.use_case.member.*;
import com.mrs.catalog_service.module.book_list.presentation.dto.request.member.AddMemberRequest;
import com.mrs.catalog_service.module.book_list.presentation.dto.request.member.UpdateMemberRoleRequest;
import com.mrs.catalog_service.module.book_list.presentation.dto.response.member.GetMembershipResponse;
import com.mrs.catalog_service.module.book_list.presentation.mapper.BookListMemberMapper;
import com.mrs.catalog_service.share.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/book/list/membership")
public class BookListMembershipController {

    private final AuthenticationService authenticationService;
    private final BookListMemberMapper memberMapper;

    private final AddMemberInBookListUseCase addMemberUseCase;
    private final DeleteMemberInBookListUseCase deleteMemberUseCase;
    private final UpdateMemberRoleUseCase updateMemberRoleUseCase;
    private final GetMembershipBookListUseCase getMembershipBookListUseCase;
    private final GetAllMembershipsOfBookListUseCase getAllMembershipsOfBookListUseCase;

    public BookListMembershipController(
            AuthenticationService authenticationService,
            BookListMemberMapper memberMapper,
            AddMemberInBookListUseCase addMemberUseCase,
            DeleteMemberInBookListUseCase deleteMemberUseCase,
            UpdateMemberRoleUseCase updateMemberRoleUseCase, GetMembershipBookListUseCase getMembershipBookListUseCase, GetAllMembershipsOfBookListUseCase getAllMembershipsOfBookListUseCase
    ) {
        this.authenticationService = authenticationService;
        this.memberMapper = memberMapper;
        this.addMemberUseCase = addMemberUseCase;
        this.deleteMemberUseCase = deleteMemberUseCase;
        this.updateMemberRoleUseCase = updateMemberRoleUseCase;
        this.getMembershipBookListUseCase = getMembershipBookListUseCase;
        this.getAllMembershipsOfBookListUseCase = getAllMembershipsOfBookListUseCase;
    }

    @PostMapping("/{bookListId}")
    @Operation(summary = "Add a new member to the book list")
    public ResponseEntity<Void> addMember(
            @PathVariable UUID bookListId,
            @RequestBody @Valid AddMemberRequest request
    ) {
        var requesterId = authenticationService.getAuthenticatedUserId();

        var command = AddMemberInBookListCommand.of(
                bookListId,
                requesterId,
                request.role(),
                request.userId()
        );

        addMemberUseCase.execute(command);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{bookListId}/{memberId}")
    @Operation(summary = "Remove a member from the book list")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID bookListId,
            @PathVariable UUID memberId
    ) {
        var requesterId = authenticationService.getAuthenticatedUserId();

        var command = DeleteMemberInBookListCommand.of(
                bookListId,
                memberId,
                requesterId
        );

        deleteMemberUseCase.execute(command);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{memberId}/role")
    @Operation(summary = "Update the role of a member in the book list")
    public ResponseEntity<Void> updateRole(
            @PathVariable UUID memberId,
            @RequestBody @Valid UpdateMemberRoleRequest request
    ) {
        var requesterId = authenticationService.getAuthenticatedUserId();

        var command = UpdateMemberRoleCommand.of(
                memberId,
                request.role(),
                requesterId
        );

        updateMemberRoleUseCase.execute(command);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{memberId}")
    @Operation(summary = "Get the membership of the authenticated user in the book list")
    public ResponseEntity<GetMembershipResponse> getMembership(
            @PathVariable UUID memberId
    ) {
        var requesterId = authenticationService.getAuthenticatedUserId();

        var command = GetMembershipQuery.of(memberId);
        var membership = getMembershipBookListUseCase.execute(command);

        return ResponseEntity.ok(memberMapper.toMembershipResponse(membership));
    }

    @GetMapping
    @Operation(
            summary = "Get all memberships of the authenticated user in the book list"
    )
    public ResponseEntity<List<GetMembershipResponse>> getAllMembershipsOfBookList(
            @RequestParam UUID bookListId
    ) {
        var requesterId = authenticationService.getAuthenticatedUserId();

        var command = GetAllMembershipOfBookListQuery.of(bookListId, requesterId);
        var memberships = getAllMembershipsOfBookListUseCase.execute(command);

        var response = memberships.stream()
                .map(memberMapper::toMembershipResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

}