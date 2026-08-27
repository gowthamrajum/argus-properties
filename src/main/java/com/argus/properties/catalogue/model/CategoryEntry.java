package com.argus.properties.catalogue.model;

import java.util.List;

/**
 * One palette family, with the shape ids in it - the index for {@code /shapes?category=}.
 *
 * @param label how the family is written for people; the UI renders this rather than title-casing
 *              the id itself, so the wording lives in one place
 */
public record CategoryEntry(String category, String label, int shapeCount, List<String> shapeIds) {
}
