package com.mrs.catalog_service.module.book_list.presentation.dto;

import java.util.List;
import java.util.UUID;

public record BookListResponse(
        UUID id,
        List<BookResponse> books,
        UUID userOwner
) {
}
