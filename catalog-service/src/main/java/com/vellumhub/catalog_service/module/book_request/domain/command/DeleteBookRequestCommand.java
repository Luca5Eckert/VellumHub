package com.vellumhub.catalog_service.module.book_request.domain.command;

public record DeleteBookRequestCommand(
        Long bookRequestId
) {
}
