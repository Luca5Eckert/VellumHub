package com.mrs.catalog_service.module.book_list.application.command.member;

import com.mrs.catalog_service.module.book_list.domain.model.MembershipRole;

import java.util.UUID;

public record UpdateMemberRoleCommand (
        MembershipRole newRole,
        UUID userAuthenticatedId,
        UUID membershipId
) {
    public static UpdateMemberRoleCommand of(UUID memberId, MembershipRole role, UUID requesterId) {
        return new UpdateMemberRoleCommand(
                role,
                requesterId,
                memberId
        );
    }
}
