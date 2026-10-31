package com.vellumhub.catalog_service.module.book_list.application.query.member;

import java.util.UUID;

public record GetMembershipQuery(
        UUID membershipId,
        UUID userAuthenticatedId
) {
    public static GetMembershipQuery of(UUID membershipId, UUID userAuthenticatedId) {
        return new GetMembershipQuery(
                membershipId,
                userAuthenticatedId
        );
    }
}
