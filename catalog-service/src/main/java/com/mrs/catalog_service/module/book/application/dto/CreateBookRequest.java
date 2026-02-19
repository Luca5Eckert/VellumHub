package com.mrs.catalog_service.module.book.application.dto;

import com.mrs.catalog_service.module.book.domain.model.Genre;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;

import java.util.List;

public record CreateBookRequest(
        @NotBlank(message = "Title is required")
        String title,

        @NotBlank(message = "Description is required")
        String description,

        @Positive(message = "Release year must be a positive number")
        int releaseYear,

        @URL(message = "Cover URL must be a valid URL")
        String coverUrl,

        @NotBlank(message = "Author is required")
        String author,

        @NotBlank(message = "ISBN is required")
        String isbn,

        @Positive(message = "Page count must be greater than zero")
        int pageCount,

        @NotBlank(message = "Publisher is required")
        String publisher,

        @NotEmpty(message = "At least one genre must be provided")
        List<Genre> genres
) {
}