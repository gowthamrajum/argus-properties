package com.argus.properties.web;

import com.argus.properties.exception.UnknownElementException;
import com.argus.properties.web.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
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

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest request) {
    log.error("Unhandled failure while serving {}", request.getRequestURI(), e);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
        "The request could not be completed.", request.getRequestURI());
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
