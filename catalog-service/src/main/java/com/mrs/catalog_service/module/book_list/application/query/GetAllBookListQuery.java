package com.mrs.catalog_service.module.book_list.application.query;

import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book_list.domain.model.TypeBookList;

import java.util.Set;
import java.util.UUID;

public record GetAllBookListQuery(
        String title,
        String description,
        UUID userOwnerList,
        Set<Genre> genres,
        Set<UUID> booksId,
        TypeBookList typeBookList,
        UUID userAuthenticated,
        int pageNumber,
        int pageSize
) {
}
