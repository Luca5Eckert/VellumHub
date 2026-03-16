package com.mrs.catalog_service.module.book_list.application.query.member;

import java.util.UUID;

public record GetMembershipQuery(
        UUID membershipId
) {
    public static GetMembershipQuery of(UUID membershipId) {
        return new GetMembershipQuery(
                membershipId
        );
    }
}
