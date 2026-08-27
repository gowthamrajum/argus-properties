package com.argus.properties.catalogue.model;

import java.util.List;
import java.util.Map;

/**
 * Every shape this build knows about, with tallies.
 *
 * <p>The counts are returned rather than left to the caller because they are the cheap sanity
 * check on a filtered query: asking for {@code ?category=GATEWAY} and seeing {@code shapeCount: 5}
 * confirms the filter did what you meant without inspecting the list.
 */
public record ShapesResponse(int shapeCount,
                             Map<String, Integer> countsByCategory,
                             List<ShapeSummary> shapes) {
}
