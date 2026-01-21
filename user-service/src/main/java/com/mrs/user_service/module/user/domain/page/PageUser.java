package com.mrs.user_service.module.user.domain.page;

public record PageUser(
        int pageSize,
        int pageNumber
) {

    public PageUser {
        pageSize = pageSize < 50 && pageSize > 0 ? pageSize : 10;
        pageNumber = Math.max(pageNumber, 0);
    }

}
