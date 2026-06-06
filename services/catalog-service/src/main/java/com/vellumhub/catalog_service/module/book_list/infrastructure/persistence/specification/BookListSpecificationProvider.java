package com.vellumhub.catalog_service.module.book_list.infrastructure.persistence.specification;

import com.vellumhub.catalog_service.module.book_list.domain.filter.BookListFilter;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookList;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookListMembership;
import com.vellumhub.catalog_service.module.book_list.domain.model.TypeBookList;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Provides JPA {@link Specification} instances for querying {@link BookList} entities
 * based on a given {@link BookListFilter}.
 *
 * <p>All predicates are combined with {@code AND} logic. Visibility rules are enforced
 * at the query level to prevent unauthorized access to private lists.</p>
 */
@Component
public class BookListSpecificationProvider {

    /**
     * Builds a {@link Specification} that applies all active filters from the given
     * {@link BookListFilter}, including text search, book-related filters, and visibility rules.
     *
     * @param filter the filter criteria to apply; fields may be {@code null} to indicate no restriction
     * @return a composed {@link Specification} representing the full query predicate
     */
    public Specification<BookList> of(BookListFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            applyTextFilters(filter, root, cb, predicates);
            applyGenresFilter(filter, root, cb, predicates);
            applyBooksIdFilter(filter, root, cb, predicates);
            applyVisibilityAndTypeFilter(filter, root, query, cb, predicates);
            applyUserOwnerFilter(filter, root, cb, predicates);

            if (query != null) {
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }




    /**
     * Applies case-insensitive partial match predicates for {@code title} and
     * {@code description} fields when present in the filter.
     *
     * @param filter     the source filter
     * @param root       the query root
     * @param cb         the criteria builder
     * @param predicates the predicate list to append to
     */
    private void applyTextFilters(BookListFilter filter, Root<BookList> root,
                                  CriteriaBuilder cb, List<Predicate> predicates) {
        if (hasText(filter.title())) {
            predicates.add(cb.like(cb.lower(root.get("title")), contains(filter.title())));
        }

        if (hasText(filter.description())) {
            predicates.add(cb.like(cb.lower(root.get("description")), contains(filter.description())));
        }
    }

    /**
     * Applies a genre filter by joining {@code books → genres}.
     *
     * <p>This join is intentionally kept separate from {@link #applyBooksIdFilter} to preserve
     * independent semantics: a list satisfies this predicate if it contains <em>any</em> book
     * whose genre matches one of the requested genres, regardless of book identity.</p>
     *
     * @param filter     the source filter
     * @param root       the query root
     * @param cb         the criteria builder
     * @param predicates the predicate list to append to
     */
    private void applyGenresFilter(BookListFilter filter, Root<BookList> root,
                                   CriteriaBuilder cb, List<Predicate> predicates) {
        if (filter.genres() == null || filter.genres().isEmpty()) return;

        Join<Object, Object> books  = root.join("books",  JoinType.INNER);
        Join<Object, Object> genres = books.join("genres", JoinType.INNER);
        predicates.add(genres.in(filter.genres()));
    }

    /**
     * Applies a book ID filter by joining {@code books}.
     *
     * <p>This join is intentionally kept separate from {@link #applyGenresFilter} to preserve
     * independent semantics: a list satisfies this predicate if it contains <em>any</em> book
     * whose ID matches one of the requested IDs, regardless of genre.</p>
     *
     * @param filter     the source filter
     * @param root       the query root
     * @param cb         the criteria builder
     * @param predicates the predicate list to append to
     */
    private void applyBooksIdFilter(BookListFilter filter, Root<BookList> root,
                                    CriteriaBuilder cb, List<Predicate> predicates) {
        if (filter.booksId() == null || filter.booksId().isEmpty()) return;

        Join<Object, Object> books = root.join("books", JoinType.INNER);
        predicates.add(books.get("id").in(filter.booksId()));
    }

    /**
     * Enforces visibility rules and optionally restricts results by {@link TypeBookList}.
     *
     * <p>Visibility is determined as follows:</p>
     * <ul>
     *   <li><strong>Unauthenticated users</strong> — only {@code PUBLIC} lists are visible.
     *       Any {@code typeBookList} value in the filter is ignored to prevent silent
     *       zero-result bugs (e.g. requesting {@code PRIVATE} without authentication).</li>
     *   <li><strong>Authenticated users</strong> — a list is visible if it is {@code PUBLIC},
     *       owned by the user, or the user holds an active membership. The {@code typeBookList}
     *       filter is then applied on top as an additional restriction.</li>
     * </ul>
     *
     * @param filter     the source filter
     * @param root       the query root
     * @param query      the criteria query, used to create the membership subquery
     * @param cb         the criteria builder
     * @param predicates the predicate list to append to
     */
    private void applyVisibilityAndTypeFilter(BookListFilter filter, Root<BookList> root,
                                              CriteriaQuery<?> query, CriteriaBuilder cb,
                                              List<Predicate> predicates) {
        if (filter.userAuthenticated() == null) {
            predicates.add(cb.equal(root.get("type"), TypeBookList.PUBLIC));
            return;
        }

        UUID userId = filter.userAuthenticated();

        Predicate isPublic = cb.equal(root.get("type"), TypeBookList.PUBLIC);
        Predicate isOwner  = cb.equal(root.get("userOwner"), userId);
        Predicate isMember = buildMemberExistsPredicate(root, query, cb, userId);
        Predicate visibility = cb.or(isPublic, isOwner, isMember);

        if (filter.typeBookList() != null) {
            predicates.add(cb.and(visibility, cb.equal(root.get("type"), filter.typeBookList())));
        } else {
            predicates.add(visibility);
        }
    }

    /**
     * Builds a correlated {@code EXISTS} subquery that returns {@code true} if the given user
     * holds at least one {@link BookListMembership} for the current {@link BookList}.
     *
     * <p>An {@code EXISTS} subquery is preferred over a {@code LEFT JOIN} to avoid row
     * duplication on the root entity and to express the "at least one" semantic correctly
     * at the database level.</p>
     *
     * @param root   the query root representing the current {@link BookList}
     * @param query  the enclosing criteria query
     * @param cb     the criteria builder
     * @param userId the authenticated user's ID
     * @return an {@code EXISTS} predicate correlated to the current root
     */
    private Predicate buildMemberExistsPredicate(Root<BookList> root, CriteriaQuery<?> query,
                                                 CriteriaBuilder cb, UUID userId) {
        Subquery<UUID> sub = query.subquery(UUID.class);
        Root<BookListMembership> membership = sub.from(BookListMembership.class);

        sub.select(membership.get("id"))
                .where(
                        cb.equal(membership.get("bookList"), root),
                        cb.equal(membership.get("userId"), userId)
                );

        return cb.exists(sub);
    }

    /**
     * Applies an {@code IN} predicate on the {@code userOwner} field when
     * {@link BookListFilter#userOwnerList()} is provided.
     *
     * <p>This predicate is combined with other filters (including visibility rules)
     * using {@code AND} semantics.</p>
     *
     * @param filter     the source filter
     * @param root       the query root
     * @param cb         the criteria builder
     * @param predicates the predicate list to append to
     */
    private void applyUserOwnerFilter(BookListFilter filter, Root<BookList> root,
                                      CriteriaBuilder cb, List<Predicate> predicates) {
        if (filter.userOwnerList() == null) {
            return;
        }
        predicates.add(cb.equal(root.get("userOwner"), filter.userOwnerList()));
    }

    /**
     * Returns {@code true} if the given string is non-null and contains non-whitespace characters.
     *
     * @param value the string to evaluate
     * @return {@code true} if the value has meaningful content
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Wraps the given value in a SQL {@code LIKE} wildcard pattern ({@code %value%}),
     * lowercased for case-insensitive matching.
     *
     * @param value the raw search term
     * @return the formatted LIKE pattern
     */
    private String contains(String value) {
        return "%" + value.toLowerCase() + "%";
    }
}