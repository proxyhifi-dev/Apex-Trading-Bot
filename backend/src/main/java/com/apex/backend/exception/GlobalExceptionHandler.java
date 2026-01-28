package com.apex.backend.exception;

import com.apex.backend.dto.ApiError;
import com.apex.backend.dto.ApiErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .collect(Collectors.toList());
        return buildError(HttpStatus.BAD_REQUEST, "Validation failed", details, request, ex);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiErrorDetail> details = ex.getConstraintViolations().stream()
                .map(violation -> ApiErrorDetail.builder()
                        .field(violation.getPropertyPath().toString())
                        .issue(violation.getMessage())
                        .build())
                .collect(Collectors.toList());
        return buildError(HttpStatus.BAD_REQUEST, "Validation failed", details, request, ex);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of(), request, ex);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage(), List.of(), request, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildError(HttpStatus.FORBIDDEN, "Forbidden", List.of(), request, ex);
    }

    @ExceptionHandler({NotFoundException.class, ResourceNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), List.of(), request, ex);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), List.of(), request, ex);
    }

    @ExceptionHandler(FyersCircuitOpenException.class)
    public ResponseEntity<ApiError> handleCircuitOpen(FyersCircuitOpenException ex, HttpServletRequest request) {
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, "Broker circuit open", List.of(), request, ex);
    }

    @ExceptionHandler({RiskLimitExceededException.class, TradingException.class})
    public ResponseEntity<ApiError> handleUnprocessable(RuntimeException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), List.of(), request, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", List.of(), request, ex);
    }

    private ApiErrorDetail toDetail(FieldError error) {
        return ApiErrorDetail.builder()
                .field(error.getField())
                .issue(error.getDefaultMessage())
                .build();
    }

    private ResponseEntity<ApiError> buildError(HttpStatus status, String message, List<ApiErrorDetail> details,
                                                HttpServletRequest request, Exception ex) {
        String requestId = MDC.get("requestId");
        String correlationId = MDC.get("correlationId");
        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorCode(status.name())
                .message(message)
                .requestId(requestId)
                .correlationId(correlationId)
                .details(details)
                .build();
        log.warn("{} {} -> {} {}", request.getMethod(), request.getRequestURI(), status.value(), message, ex);
        return ResponseEntity.status(status).body(error);
    }
}
