package com.argus.properties.web.dto;

/**
 * One thing this service can answer, as a path.
 *
 * <p>Returned by {@code /capabilities} so a caller can discover the API without reading the
 * OpenAPI document - the same convention argus-backend uses.
 */
public record Capability(String id, String name, String description, String method, String path) {
}
