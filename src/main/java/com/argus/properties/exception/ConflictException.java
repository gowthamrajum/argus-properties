package com.argus.properties.exception;

/** A write that would duplicate something already there. Rendered as 409. */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
