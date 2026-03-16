package com.mrs.catalog_service.module.book_list.application.query.member;

import java.util.UUID;

public record GetAllMembershipOfBookListQuery(
        UUID bookListId,
        UUID userAuthenticatedId
) {
}
