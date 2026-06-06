package com.vellumhub.catalog_service.module.book_list.presentation.dto.request.member;

import com.vellumhub.catalog_service.module.book_list.domain.model.MembershipRole;

public record UpdateMemberRoleRequest(
        MembershipRole role
) {
}
