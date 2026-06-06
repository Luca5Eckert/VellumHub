package com.vellumhub.catalog_service.module.book_list.domain.port;

import com.vellumhub.catalog_service.module.book_list.domain.model.BookListMembership;

import java.util.Optional;
import java.util.UUID;

public interface MembershipBookListRepository {
    Optional<BookListMembership> findById(UUID uuid);

    void save(BookListMembership membership);
}
