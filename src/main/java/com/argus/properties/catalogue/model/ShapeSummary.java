package com.argus.properties.catalogue.model;

/**
 * A shape as it appears in a list.
 *
 * <p>{@code /shapes} returns fifty of these. Sending each one's full property list would make the
 * listing an order of magnitude larger than the thing most callers want from it - a palette, a
 * picker, a table of contents - so the detail lives one hop away at {@code /shapes/&#123;id&#125;}
 * and {@code propertyCount} tells you whether the hop is worth making.
 */
public record ShapeSummary(String id,
                           String name,
                           String tag,
                           String category,
                           String summary,
                           boolean executable,
                           int propertyCount) {

  public static ShapeSummary of(Shape shape, int effectivePropertyCount) {
    return new ShapeSummary(shape.id(), shape.name(), shape.tag(), shape.category(),
        shape.summary(), shape.executable(), effectivePropertyCount);
  }
}
