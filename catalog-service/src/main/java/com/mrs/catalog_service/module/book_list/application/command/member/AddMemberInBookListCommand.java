package com.mrs.catalog_service.module.book_list.application.command.member;

import com.mrs.catalog_service.module.book_list.domain.model.MembershipRole;

import java.util.UUID;

public record AddMemberInBookListCommand(
        UUID bookListId,
        UUID userAuthenticatedId,
        UUID userIdToAdd,
        MembershipRole role
) {

    public static AddMemberInBookListCommand of(UUID bookListId, UUID userAuthenticatedId, MembershipRole role, UUID userIdToAdd) {
        return new AddMemberInBookListCommand(
                bookListId,
                userAuthenticatedId,
                userIdToAdd,
                role
        );
    }

}
