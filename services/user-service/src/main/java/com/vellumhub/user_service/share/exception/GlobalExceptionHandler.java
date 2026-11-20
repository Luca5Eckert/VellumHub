package com.vellumhub.user_service.share.exception;

import com.vellumhub.user_service.module.user.application.exception.UserApplicationException;
import com.vellumhub.user_service.module.user.application.exception.UserNotFoundException;
import com.vellumhub.user_service.module.user.domain.exception.UserDomainException;
import com.vellumhub.user_service.module.user_preference.domain.exception.UserPreferenceDomainException;
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
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @ExceptionHandler(UserDomainException.class)
    public ResponseEntity<ApiResponseError> handleUserDomainException(
            UserDomainException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "User Domain Error", ex.getMessage(),
                "Business rule violation in user domain", request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponseError> handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "Not Found", "User not found", "Resource unavailable", request);
    }

    @ExceptionHandler(UserApplicationException.class)
    public ResponseEntity<ApiResponseError> handleUserApplicationException(
            UserApplicationException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "User Application Error", ex.getMessage(),
                "Error during application service orchestration", request);
    }

    @ExceptionHandler(UserPreferenceDomainException.class)
    public ResponseEntity<ApiResponseError> handleUserPreferenceDomainException(
            UserPreferenceDomainException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "User Preference Domain Error", ex.getMessage(),
                "Business rule violation in user preferences", request);
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

    // Spring MVC routes missing handlers to the resource fallback. Preserve 404 semantics
    // instead of treating removed legacy authentication endpoints as server failures.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponseError> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "Not Found", "No resource found for the requested path",
                "The requested endpoint does not exist", request);
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
