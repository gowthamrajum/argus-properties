package com.argus.properties.catalogue.model;

import java.util.List;

/**
 * One idea this catalogue depends on, explained the way you would explain it to a colleague.
 *
 * @param term         what it is called here
 * @param inShort      one sentence, no jargon
 * @param explanation  the fuller version, still conversational
 * @param example      something concrete, because the sentence above is never quite enough
 * @param related      ids of other concepts worth reading next
 */
public record Concept(String id,
                      String term,
                      String inShort,
                      String explanation,
                      String example,
                      List<String> related) {

  public Concept {
    related = related == null ? List.of() : List.copyOf(related);
  }
}
