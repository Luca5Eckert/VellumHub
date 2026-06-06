package com.vellumhub.catalog_service.module.book_list.application.query.list;

import java.util.UUID;

public record GetBookListByIdQuery(
        UUID userId,
        UUID bookListId
) {

    public static GetBookListByIdQuery of(
            UUID userId,
            UUID bookListId
    ) {
        return new GetBookListByIdQuery(
                userId,
                bookListId
        );
    }

}
