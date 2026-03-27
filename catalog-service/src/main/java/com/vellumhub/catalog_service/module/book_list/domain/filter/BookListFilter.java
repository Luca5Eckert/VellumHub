package com.vellumhub.catalog_service.module.book_list.domain.filter;

import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book_list.domain.model.TypeBookList;

import java.util.Set;
import java.util.UUID;

public record BookListFilter(
        String title,
        String description,
        UUID userOwnerList,
        Set<Genre> genres,
        Set<UUID> booksId,
        TypeBookList typeBookList,
        UUID userAuthenticated
) {
    public static BookListFilter of(
            String title,
            String description,
            UUID userOwnerList,
            Set<Genre> genres,
            Set<UUID> booksId,
            TypeBookList typeBookList,
            UUID userAuthenticated
    ) {
        return new BookListFilter(
                title,
                description,
                userOwnerList,
                genres,
                booksId,
                typeBookList,
                userAuthenticated
        );
    }

}
