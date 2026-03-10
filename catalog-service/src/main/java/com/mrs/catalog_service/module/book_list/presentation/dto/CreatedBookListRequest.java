package com.mrs.catalog_service.module.book_list.presentation.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CreatedBookListRequest(
        @NotEmpty List<UUID> booksId
) {
}
