package com.mrs.catalog_service.module.book_list.presentation.dto.request.member;

import com.mrs.catalog_service.module.book_list.domain.model.MembershipRole;

public record UpdateMemberRoleRequest(
        MembershipRole role
) {
}
