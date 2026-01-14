package com.mrs.user_service.exception;

import com.mrs.user_service.exception.application.UserApplicationException;
import com.mrs.user_service.exception.domain.user.UserDomainException;
import com.mrs.user_service.exception.domain.user_preference.UserPreferenceDomainException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponseError> handlerRuntimeException(
            RuntimeException ex,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;

        ApiResponseError apiResponseError = ApiResponseError.builder()
                .status(status.value())
                .error("Runtime Error")
                .message(ex.getMessage())
                .details(List.of("An unexpected error occurred on the server side"))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(status).body(apiResponseError);
    }

    @ExceptionHandler(UserDomainException.class)
    public ResponseEntity<ApiResponseError> handlerUserDomainException(
            UserDomainException ex,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;

        ApiResponseError apiResponseError = ApiResponseError.builder()
                .status(status.value())
                .error("User Domain Error")
                .message(ex.getMessage())
                .details(List.of("An unexpected error occurred on the server side"))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(status).body(apiResponseError);
    }

    @ExceptionHandler(UserApplicationException.class)
    public ResponseEntity<ApiResponseError> handlerUserApplicationException(
            UserApplicationException ex,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;

        ApiResponseError apiResponseError = ApiResponseError.builder()
                .status(status.value())
                .error("User Application Error")
                .message(ex.getMessage())
                .details(List.of("An unexpected error occurred on the server side"))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(status).body(apiResponseError);
    }

    @ExceptionHandler(UserPreferenceDomainException.class)
    public ResponseEntity<ApiResponseError> handlerUserDomainException(
            UserPreferenceDomainException ex,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;

        ApiResponseError apiResponseError = ApiResponseError.builder()
                .status(status.value())
                .error("User Preference Domain Error")
                .message(ex.getMessage())
                .details(List.of("An unexpected error occurred on the server side"))
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(status).body(apiResponseError);
    }


}