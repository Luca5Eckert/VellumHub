package com.vellumhub.catalog_service.share.exception;

import com.vellumhub.catalog_service.module.book.domain.exception.BookWritesFrozenException;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListPermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/book-lists/42");

    @Test
    void permissionDeniedKeepsForbiddenContract() {
        ResponseEntity<ApiResponseError> response =
                handler.handleBookListPermissionDenied(new BookListPermissionDeniedException(), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        ApiResponseError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(403);
        assertThat(body.error()).isEqualTo("Forbidden");
        assertThat(body.message()).isEqualTo("Insufficient permission");
        assertThat(body.details()).isEqualTo(List.of("List operation denied"));
        assertThat(body.path()).isEqualTo("/book-lists/42");
        assertThat(body.timestamp()).isNotNull();
        assertThat(response.getHeaders().containsHeader(HttpHeaders.RETRY_AFTER)).isFalse();
    }

    @Test
    void frozenWritesReturnServiceUnavailableWithRetryAfter() {
        ResponseEntity<ApiResponseError> response =
                handler.handleBookWritesFrozen(new BookWritesFrozenException(), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        ApiResponseError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.error()).isEqualTo("Service Unavailable");
        assertThat(body.message()).isEqualTo("Book writes temporarily frozen");
        assertThat(body.details()).isEqualTo(List.of("Retry the Book mutation later"));
    }

    @Test
    void optimisticLockConflictMapsToConflict() {
        ResponseEntity<ApiResponseError> response = handler.handleConcurrentProgressUpdate(
                new ObjectOptimisticLockingFailureException(Object.class, "id"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Concurrent Update");
        assertThat(response.getBody().details()).isEqualTo(List.of("Optimistic version mismatch"));
    }

    @Test
    void unexpectedExceptionDoesNotLeakInternals() {
        ResponseEntity<ApiResponseError> response =
                handler.handleGenericException(new IllegalStateException("db password=secret"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).doesNotContain("secret");
        assertThat(response.getBody().details()).isEqualTo(List.of("Please contact technical support"));
    }
}
