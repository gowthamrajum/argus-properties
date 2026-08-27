package com.argus.properties.catalogue.model;

import java.util.List;

/**
 * One property, and everywhere in the catalogue it applies.
 *
 * <p>The catalogue is indexed by shape, which answers "what can I set here?" but not the question
 * people actually arrive with: "which shapes support task listeners?" Answering that by opening
 * fifty-one pages is not answering it. This is the same data read along the other axis.
 *
 * @param occurrences empty on the listing, populated on {@code /properties/&#123;name&#125;} - the
 *                    listing would otherwise be an order of magnitude larger than the index anyone
 *                    wants to scan
 */
public record PropertyUsage(String name,
                            String label,
                            String namespace,
                            String kind,
                            int shapeCount,
                            List<Occurrence> occurrences) {

  public PropertyUsage {
    occurrences = occurrences == null ? List.of() : List.copyOf(occurrences);
  }

  /**
   * The property as it appears on one shape.
   *
   * <p>The description is carried per occurrence rather than once, because the same property does
   * not always mean quite the same thing: an execution listener on a sequence flow fires on
   * {@code take}, and on a flow node it fires on {@code start} or {@code end}. Collapsing those to
   * one sentence would lose the only part worth reading.
   *
   * @param inheritedFrom the group it came from, or null when the shape declares it itself
   */
  public record Occurrence(String shapeId,
                           String shapeName,
                           String category,
                           String inheritedFrom,
                           String description) {
  }
}
