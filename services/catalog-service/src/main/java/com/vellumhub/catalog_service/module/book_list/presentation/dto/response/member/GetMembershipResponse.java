package com.vellumhub.catalog_service.module.book_list.presentation.dto.response.member;

import com.vellumhub.catalog_service.module.book_list.domain.model.MembershipRole;

import java.time.Instant;
import java.util.UUID;

public record GetMembershipResponse(
        UUID id,
        UUID bookListId,
        UUID userId,
        MembershipRole role,
        Instant createdAt,
        Instant updatedAt
) {
}
