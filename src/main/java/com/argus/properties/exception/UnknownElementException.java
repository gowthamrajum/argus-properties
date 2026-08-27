package com.argus.properties.exception;

/** A shape, property or group id that is not in the catalogue. Rendered as 404. */
public class UnknownElementException extends RuntimeException {

  public UnknownElementException(String message) {
    super(message);
  }
}
