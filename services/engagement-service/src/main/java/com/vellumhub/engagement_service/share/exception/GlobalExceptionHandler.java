package com.vellumhub.engagement_service.share.exception;

import com.vellumhub.engagement_service.module.rating.application.exception.EngagementApplicationException;
import com.vellumhub.engagement_service.module.rating.domain.exception.RatingDomainException;
import com.vellumhub.engagement_service.module.rating.domain.exception.RatingNotFoundException;
import com.vellumhub.engagement_service.module.reaction.domain.exception.ReactionNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Maps exceptions to the stable {@link ApiResponseError} contract.
 *
 * <p>Each handler only declares status, error label, message and detail; the envelope
 * (path, timestamp) is assembled by the private helpers at the bottom. Database and parser
 * internals are logged server-side and never returned to the client.</p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseError> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "Validation Error", "Input validation failed", fieldErrors, request);
    }

    /** Private ratings/reactions answer 404 so callers cannot probe for their existence. */
    @ExceptionHandler({RatingNotFoundException.class, ReactionNotFoundException.class})
    public ResponseEntity<ApiResponseError> handlePrivateResourceHidden(RuntimeException ex, HttpServletRequest request) {
        String resource = ex instanceof RatingNotFoundException ? "rating" : "reaction";
        logger.info("authorization_decision resource={} decision=not_visible", resource);
        return respond(HttpStatus.NOT_FOUND, "Not Found", "Resource not found", "Resource unavailable", request);
    }

    @ExceptionHandler(RatingDomainException.class)
    public ResponseEntity<ApiResponseError> handleRatingDomainException(
            RatingDomainException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Rating Domain Error", ex.getMessage(),
                "Business rule violation in rating domain", request);
    }

    @ExceptionHandler(EngagementApplicationException.class)
    public ResponseEntity<ApiResponseError> handleEngagementApplicationException(
            EngagementApplicationException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Engagement Application Error", ex.getMessage(),
                "Error during application service orchestration", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponseError> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        logger.warn("Data integrity violation on path {}: {}", request.getRequestURI(),
                integrityViolationSummary(ex));
        return respond(HttpStatus.CONFLICT, "Data Integrity Error", "Database constraint violation",
                "Database constraint violation", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponseError> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        logger.debug("Unreadable request body on path {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST, "Malformed Request",
                "Required request body is missing or invalid", "The request body could not be parsed", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponseError> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String detail = String.format("Parameter '%s' with value '%s' could not be converted to '%s'",
                ex.getName(), ex.getValue(), Objects.requireNonNull(ex.getRequiredType()).getSimpleName());
        return respond(HttpStatus.BAD_REQUEST, "Type Mismatch", "Invalid parameter type in URL", detail, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponseError> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", ex.getMessage(),
                "Supported methods: " + Arrays.toString(ex.getSupportedMethods()), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponseError> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Missing Parameter", ex.getMessage(),
                "The required parameter '" + ex.getParameterName() + "' is missing", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseError> handleGenericException(Exception ex, HttpServletRequest request) {
        logger.error("Unexpected error on path {}: ", request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred on the server side", "Please contact technical support", request);
    }

    /**
     * Identifies the violated rule without logging the database message, which for
     * PostgreSQL contains the conflicting values (for example an e-mail address).
     */
    static String integrityViolationSummary(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return "constraint=" + violation.getConstraintName();
            }
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return "sqlState=" + sql.getSQLState();
            }
        }
        return "cause=" + ex.getMostSpecificCause().getClass().getSimpleName();
    }

    private static ResponseEntity<ApiResponseError> respond(
            HttpStatus status, String error, String message, String detail, HttpServletRequest request) {
        return respond(status, error, message, List.of(detail), request);
    }

    private static ResponseEntity<ApiResponseError> respond(
            HttpStatus status, String error, String message, List<String> details, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponseError.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .details(details)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }
}
