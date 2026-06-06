package com.vellumhub.catalog_service.module.book_list.application.command.member;

import java.util.UUID;

public record DeleteMemberInBookListCommand(
            UUID userAuthenticatedId,
            UUID userIdToDelete,
            UUID bookListId
) {
    public static DeleteMemberInBookListCommand of(UUID bookListId, UUID memberId, UUID requesterId) {
        return new DeleteMemberInBookListCommand(
                requesterId,
                memberId,
                bookListId
        );
    }
}
