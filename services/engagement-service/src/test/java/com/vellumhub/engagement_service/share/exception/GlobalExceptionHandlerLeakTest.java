package com.vellumhub.engagement_service.share.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Database and JSON parser internals must stay in server logs, never in API error bodies. */
class GlobalExceptionHandlerLeakTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/resource");

    @Test
    void dataIntegrityViolationDoesNotExposeConstraintOrSql() {
        var violation = new DataIntegrityViolationException("could not execute statement",
                new SQLException("duplicate key value violates unique constraint \"uk_secret_table\""));

        ResponseEntity<ApiResponseError> response = handler.handleDataIntegrityViolationException(violation, request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().details()).isEqualTo(List.of("Database constraint violation"));
        assertThat(response.getBody().toString()).doesNotContain("uk_secret_table");
    }

    @Test
    void dataIntegrityViolationWithoutCauseMessageStillReturnsConflict() {
        var violation = new DataIntegrityViolationException("violation", new SQLException((String) null));

        ResponseEntity<ApiResponseError> response = handler.handleDataIntegrityViolationException(violation, request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void unreadableBodyDoesNotExposeParserOrClassNames() {
        var unreadable = new HttpMessageNotReadableException(
                "JSON parse error: Cannot deserialize value of type `com.vellumhub.Internal`",
                new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ApiResponseError> response = handler.handleHttpMessageNotReadableException(unreadable, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Malformed Request");
        assertThat(response.getBody().details()).isEqualTo(List.of("The request body could not be parsed"));
        assertThat(response.getBody().toString()).doesNotContain("com.vellumhub").doesNotContain("Cannot deserialize");
    }

    @Test
    void integrityViolationLogSummaryOmitsConflictingValues() {
        var violation = new DataIntegrityViolationException("could not execute statement",
                new SQLException("Key (email)=(reader@example.com) already exists", "23505"));

        String summary = GlobalExceptionHandler.integrityViolationSummary(violation);

        assertThat(summary).isEqualTo("sqlState=23505").doesNotContain("reader@example.com");
    }
}
