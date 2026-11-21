package com.vellumhub.catalog_service.module.book.infrastructure.repository.book;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaBookRepository extends JpaRepository<Book, UUID> {
    boolean existsByTitleAndAuthor(String title, String author);

    boolean existsByTitleAndAuthorAndIsbn(String title, String author, String isbn);

    boolean existsByIsbn(String isbn);

    @Query(value = """
            SELECT book.*
            FROM books book
            CROSS JOIN plainto_tsquery('pg_catalog.simple', :text) search(query)
            WHERE book.deleted_at IS NULL
              AND catalog_book_search_document(book.title, book.author, book.description, book.publisher) @@ search.query
            ORDER BY ts_rank_cd(
                catalog_book_search_document(book.title, book.author, book.description, book.publisher), search.query
            ) DESC, book.created_at, book.id
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Book> search(@Param("text") String text, @Param("limit") int limit, @Param("offset") long offset);
}
