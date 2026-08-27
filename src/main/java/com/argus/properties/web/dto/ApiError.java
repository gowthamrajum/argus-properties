package com.argus.properties.web.dto;

import java.time.Instant;

/** Error body: enough to act on, nothing that leaks internals. Mirrors argus-backend's shape. */
public record ApiError(Instant timestamp, int status, String error, String code, String message, String path) {
}
