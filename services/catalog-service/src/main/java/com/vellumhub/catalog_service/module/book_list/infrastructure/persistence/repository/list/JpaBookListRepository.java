package com.vellumhub.catalog_service.module.book_list.infrastructure.persistence.repository.list;

import com.vellumhub.catalog_service.module.book_list.domain.model.BookList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaBookListRepository extends JpaRepository<BookList, UUID>, JpaSpecificationExecutor<BookList> {

    @Query("""
           SELECT DISTINCT bl FROM BookList bl
           LEFT JOIN FETCH bl.memberships
           WHERE bl.id = :id
           """)
    Optional<BookList> findByIdFull(@Param("id") UUID id);

}
