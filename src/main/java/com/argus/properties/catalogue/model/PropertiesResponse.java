package com.argus.properties.catalogue.model;

import java.util.List;
import java.util.Map;

/**
 * The effective property set of one shape: what it declares, plus everything it inherits.
 *
 * <p>Ordering is inherited-first, then own. That reads correctly top-down - the general BPMN
 * plumbing you can skim ({@code id}, {@code name}, {@code incoming}) before the properties that
 * make this shape what it is - and it keeps the same property at the same index across shapes
 * that share groups.
 */
public record PropertiesResponse(String shapeId,
                                 String shape,
                                 String tag,
                                 int propertyCount,
                                 Map<String, Integer> countsByNamespace,
                                 Map<String, Integer> countsByKind,
                                 List<Property> properties) {
}
