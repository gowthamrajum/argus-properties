package com.argus.properties.web;

import com.argus.properties.exception.ConflictException;
import com.argus.properties.exception.UnknownElementException;
import com.argus.properties.web.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders every failure as an {@link ApiError}.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} for the same reason argus-backend does: Spring
 * MVC raises typed exceptions for protocol-level problems that already carry the right status, and
 * without the base class the catch-all below would flatten all of them into 500s.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(UnknownElementException.class)
  public ResponseEntity<ApiError> handleUnknown(UnknownElementException e, HttpServletRequest request) {
    // The message names the listing endpoint, so a 404 tells the caller how to find the right id.
    return build(HttpStatus.NOT_FOUND, "UNKNOWN_ELEMENT", e.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiError> handleConflict(ConflictException e, HttpServletRequest request) {
    // The message names what already exists and what to do instead, so a 409 is actionable.
    return build(HttpStatus.CONFLICT, "CONFLICT", e.getMessage(), request.getRequestURI());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest request) {
    log.error("Unhandled failure while serving {}", request.getRequestURI(), e);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
        "The request could not be completed.", request.getRequestURI());
  }

  /**
   * Renders bean-validation failures as the messages the constraints actually declare.
   *
   * <p>Spring's default is the whole exception toString - class names, parameter indexes, resolved
   * message codes, then the useful part somewhere near the end. The UI shows server messages
   * verbatim on the assumption they are worth reading, so they have to be.
   */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                HttpHeaders headers,
                                                                HttpStatusCode status,
                                                                WebRequest request) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .distinct()
        .collect(Collectors.joining("; "));
    String path = request instanceof ServletWebRequest servletRequest
        ? servletRequest.getRequest().getRequestURI()
        : null;

    return new ResponseEntity<>(new ApiError(Instant.now(), status.value(), "Bad Request",
        "VALIDATION_FAILED", message.isBlank() ? "The request body is not valid." : message, path),
        headers, status);
  }
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                           @Nullable Object body,
                                                           HttpHeaders headers,
                                                           HttpStatusCode statusCode,
                                                           WebRequest request) {
    HttpStatus status = HttpStatus.resolve(statusCode.value());
    String code = status == null ? "REQUEST_FAILED" : status.name();
    String reason = status == null ? "Error" : status.getReasonPhrase();
    String path = request instanceof ServletWebRequest servletRequest
        ? servletRequest.getRequest().getRequestURI()
        : null;

    return new ResponseEntity<>(
        new ApiError(Instant.now(), statusCode.value(), reason, code, ex.getMessage(), path),
        headers, statusCode);
  }

  private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message, String path) {
    return ResponseEntity.status(status)
        .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path));
  }
}
