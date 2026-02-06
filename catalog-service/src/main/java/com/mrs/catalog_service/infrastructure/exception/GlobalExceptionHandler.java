package com.mrs.catalog_service.infrastructure.exception;

import com.mrs.catalog_service.application.exception.MediaApplicationException;
import com.mrs.catalog_service.domain.exception.MediaDomainException;
import com.mrs.catalog_service.domain.exception.MediaNotExistException;
import com.mrs.catalog_service.domain.exception.MediaNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseError> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return ResponseEntity.status(status).body(ApiResponseError.builder()
                .status(status.value())
                .error("Validation Error")
                .message("Input validation failed")
                .details(errors)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<ApiResponseError> handleMediaNotFoundException(
            MediaNotFoundException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(ApiResponseError.builder()
                .status(status.value())
                .error("Book Not Found")
                .message(ex.getMessage())
                .details(List.of("The requested book resource was not found"))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(MediaNotExistException.class)
    public ResponseEntity<ApiResponseError> handleMediaNotExistException(
            MediaNotExistException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(ApiResponseError.builder()
                .status(status.value())
                .error("Book Not Found")
                .message(ex.getMessage())
                .details(List.of("The requested book resource does not exist"))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(MediaDomainException.class)
    public ResponseEntity<ApiResponseError> handleMediaDomainException(
            MediaDomainException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponseError.builder()
                .status(status.value())
                .error("Book Domain Error")
                .message(ex.getMessage())
                .details(List.of("Business rule violation in book domain"))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(MediaApplicationException.class)
    public ResponseEntity<ApiResponseError> handleMediaApplicationException(
            MediaApplicationException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponseError.builder()
                .status(status.value())
                .error("Book Application Error")
                .message(ex.getMessage())
                .details(List.of("Error during application service orchestration"))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponseError> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(ApiResponseError.builder()
                .status(status.value())
                .error("Data Integrity Error")
                .message("Database constraint violation")
                .details(List.of(Objects.requireNonNull(ex.getMostSpecificCause().getMessage())))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponseError> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponseError.builder()
                .status(status.value())
                .error("Malformed Request")
                .message("Required request body is missing or invalid")
                .details(List.of(ex.getLocalizedMessage()))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponseError> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;
        String detail = String.format("Parameter '%s' with value '%s' could not be converted to '%s'",
                ex.getName(), ex.getValue(), Objects.requireNonNull(ex.getRequiredType()).getSimpleName());

        return ResponseEntity.status(status).body(ApiResponseError.builder()
                .status(status.value())
                .error("Type Mismatch")
                .message("Invalid parameter type in URL")
                .details(List.of(detail))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponseError> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;
        return ResponseEntity.status(status).body(ApiResponseError.builder()
                .status(status.value())
                .error("Method Not Allowed")
                .message(ex.getMessage())
                .details(List.of("Supported methods: " + java.util.Arrays.toString(ex.getSupportedMethods())))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponseError> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponseError.builder()
                .status(status.value())
                .error("Missing Parameter")
                .message(ex.getMessage())
                .details(List.of("The required parameter '" + ex.getParameterName() + "' is missing"))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseError> handleGenericException(
            Exception ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        logger.error("Unexpected error on path {}: ", request.getRequestURI(), ex);

        return ResponseEntity.status(status).body(ApiResponseError.builder()
                .status(status.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred on the server side")
                .details(List.of("Please contact technical support"))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }
}
