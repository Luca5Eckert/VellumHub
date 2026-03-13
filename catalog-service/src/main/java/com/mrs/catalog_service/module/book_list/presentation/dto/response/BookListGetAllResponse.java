package com.mrs.catalog_service.module.book_list.presentation.dto.response;

import java.util.List;
import java.util.UUID;

public record BookListGetAllResponse(
        UUID id,
        List<UUID> booksIds,
        UUID userOwner
) {
}
