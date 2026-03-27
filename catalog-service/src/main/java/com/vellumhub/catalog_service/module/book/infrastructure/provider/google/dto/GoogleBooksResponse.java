package com.vellumhub.catalog_service.module.book.infrastructure.provider.google.dto;

import java.util.List;

public record GoogleBooksResponse(List<GoogleBookItem> items) {
    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }
}
