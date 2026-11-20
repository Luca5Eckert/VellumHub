package com.vellumhub.catalog_service.module.book.application.query;

/**
 * Normalized pagination for catalog book listing.
 *
 * <p>Out-of-range sizes fall back to {@value #DEFAULT_PAGE_SIZE} and negative page numbers to
 * {@code 0}. Normalizing before the cache lookup guarantees that cache keys match the actual query.</p>
 */
public record PageBook(
        int pageSize,
        int pageNumber
) {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE_EXCLUSIVE = 50;

    public PageBook {
        pageSize = pageSize > 0 && pageSize < MAX_PAGE_SIZE_EXCLUSIVE ? pageSize : DEFAULT_PAGE_SIZE;
        pageNumber = Math.max(pageNumber, 0);
    }
}
