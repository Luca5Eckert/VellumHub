package com.vellumhub.catalog_service.module.book_list.infrastructure.persistence.repository.member;

import com.vellumhub.catalog_service.module.book_list.domain.model.BookListMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaMembershipBookListRepository extends JpaRepository<BookListMembership, UUID> {
}
