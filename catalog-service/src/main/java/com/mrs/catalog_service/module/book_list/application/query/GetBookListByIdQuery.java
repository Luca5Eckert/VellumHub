package com.mrs.catalog_service.module.book_list.application.query;

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
