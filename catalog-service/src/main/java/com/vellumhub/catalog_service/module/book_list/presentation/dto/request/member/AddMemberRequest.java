package com.vellumhub.catalog_service.module.book_list.presentation.dto.request.member;

import com.vellumhub.catalog_service.module.book_list.domain.model.MembershipRole;

import java.util.UUID;

public record AddMemberRequest(
        UUID userId,
        MembershipRole role
) {
}
