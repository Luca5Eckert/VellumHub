package com.vellumhub.catalog_service.module.book.domain.exception;

public class ExternalBookProviderException extends RuntimeException {
    public ExternalBookProviderException(Throwable cause) {
        super("External catalog service is currently unavailable", cause);
    }
}
