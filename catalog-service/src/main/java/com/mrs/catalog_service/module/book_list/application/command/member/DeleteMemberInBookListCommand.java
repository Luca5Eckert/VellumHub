package com.mrs.catalog_service.module.book_list.application.command.member;

import java.util.UUID;

public record DeleteMemberInBookListCommand(
            UUID userAuthenticatedId,
            UUID userIdToDelete,
            UUID bookListId
) {
}
