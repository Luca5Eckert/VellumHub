package com.mrs.catalog_service.module.book_list.presentation.dto;

import com.mrs.catalog_service.module.book_list.domain.model.TypeBookList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatedBookListRequest(
        @NotBlank @Size(max = 25) String title,
        @Size(max = 100) String description,
        @NotNull TypeBookList type,
        List<UUID> booksId
) {
}
