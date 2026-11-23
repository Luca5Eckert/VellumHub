package com.vellumhub.catalog_service.share.exception;

import com.vellumhub.catalog_service.module.book.domain.exception.BookDomainException;
import com.vellumhub.catalog_service.module.book.domain.exception.BookNotExistException;
import com.vellumhub.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.vellumhub.catalog_service.module.book.domain.exception.BookWritesFrozenException;
import com.vellumhub.catalog_service.module.book.domain.exception.ExternalBookProviderException;
import com.vellumhub.catalog_service.module.book.infrastructure.cache.CacheLoadOverloadedException;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListPermissionDeniedException;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListResourceNotFoundException;
import com.vellumhub.catalog_service.module.book_list.domain.exception.MembershipBookListDomainException;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressConflictException;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressDomainException;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressNotFoundException;
import com.vellumhub.catalog_service.module.book_request.domain.exception.BookRequestDomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
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
 * (path, timestamp, headers) is assembled by the private helpers at the bottom. Database and
 * parser internals are logged server-side and never returned to the client.</p>
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

    @ExceptionHandler(ExternalBookProviderException.class)
    public ResponseEntity<ApiResponseError> handleExternalBookProvider(HttpServletRequest request) {
        return respond(HttpStatus.BAD_GATEWAY, "External Catalog Error",
                "External catalog service is currently unavailable",
                "Retry the external catalog request later", request);
    }

    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<ApiResponseError> handleBookNotFoundException(
            BookNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "Book Not Found", ex.getMessage(),
                "The requested book resource was not found", request);
    }

    @ExceptionHandler(BookNotExistException.class)
    public ResponseEntity<ApiResponseError> handleBookNotExistException(
            BookNotExistException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "Book Not Found", ex.getMessage(),
                "The requested book does not exist", request);
    }

    @ExceptionHandler(BookRequestDomainException.class)
    public ResponseEntity<ApiResponseError> handleBookRequestDomainException(
            BookRequestDomainException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Book Request Error", ex.getMessage(),
                "Business rule violation in book domain", request);
    }

    @ExceptionHandler(BookListPermissionDeniedException.class)
    public ResponseEntity<ApiResponseError> handleBookListPermissionDenied(
            BookListPermissionDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "Forbidden", "Insufficient permission",
                "List operation denied", request);
    }

    @ExceptionHandler(BookListResourceNotFoundException.class)
    public ResponseEntity<ApiResponseError> handleBookListResourceNotFound(
            BookListResourceNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "Not Found", "Book list resource not found",
                "Resource unavailable", request);
    }

    @ExceptionHandler({BookListDomainException.class, MembershipBookListDomainException.class})
    public ResponseEntity<ApiResponseError> handleBookListDomainException(
            RuntimeException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Book List Error", ex.getMessage(),
                "Book list business rule violation", request);
    }

    @ExceptionHandler(CacheLoadOverloadedException.class)
    public ResponseEntity<ApiResponseError> handleCatalogCacheOverloaded(
            CacheLoadOverloadedException ex, HttpServletRequest request) {
        return respondRetryLater(1, ex.getMessage(), "Catalog read capacity temporarily exhausted", request);
    }

    @ExceptionHandler(BookWritesFrozenException.class)
    public ResponseEntity<ApiResponseError> handleBookWritesFrozen(
            BookWritesFrozenException ex, HttpServletRequest request) {
        // Controller advice runs before @ResponseStatus; the generic handler would return 500.
        return respondRetryLater(5, ex.getMessage(), "Retry the Book mutation later", request);
    }

    @ExceptionHandler(BookDomainException.class)
    public ResponseEntity<ApiResponseError> handleBookDomainException(
            BookDomainException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Book Domain Error", ex.getMessage(),
                "Business rule violation in book domain", request);
    }

    @ExceptionHandler(BookProgressDomainException.class)
    public ResponseEntity<ApiResponseError> handleBookProgressDomainException(
            BookProgressDomainException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Book Progress Error", ex.getMessage(),
                "Business rule violation in book progress domain", request);
    }

    @ExceptionHandler(BookProgressConflictException.class)
    public ResponseEntity<ApiResponseError> handleBookProgressConflictException(
            BookProgressConflictException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "Book Progress Conflict", ex.getMessage(),
                "The requested operation conflicts with the current book progress state", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponseError> handleConcurrentProgressUpdate(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "Concurrent Update",
                "Reading progress was modified concurrently; reload and retry",
                "Optimistic version mismatch", request);
    }

    @ExceptionHandler(BookProgressNotFoundException.class)
    public ResponseEntity<ApiResponseError> handleBookProgressNotFoundException(
            BookProgressNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "Book Progress Not Found", ex.getMessage(),
                "The requested book progress resource was not found", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseError> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "Access Denied",
                "You do not have permission to access this resource",
                "The authenticated user is not authorized to perform this operation", request);
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
        return ResponseEntity.status(status).body(body(status, error, message, details, request));
    }

    private static ResponseEntity<ApiResponseError> respondRetryLater(
            int retryAfterSeconds, String message, String detail, HttpServletRequest request) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(body(status, "Service Unavailable", message, List.of(detail), request));
    }

    private static ApiResponseError body(
            HttpStatus status, String error, String message, List<String> details, HttpServletRequest request) {
        return ApiResponseError.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .details(details)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
    }
}
