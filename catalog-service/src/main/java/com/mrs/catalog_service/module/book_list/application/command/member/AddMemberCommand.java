package com.mrs.catalog_service.module.book_list.application.command.member;

import com.mrs.catalog_service.module.book_list.domain.model.MembershipRole;

import java.util.UUID;

public record AddMemberCommand(
        UUID bookListId,
        UUID userAuthenticatedId,
        UUID userIdToAdd,
        MembershipRole role
) {

}
