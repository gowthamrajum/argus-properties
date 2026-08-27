package com.argus.properties.catalogue.model;

import java.util.List;

/**
 * One rule in the legality matrix: this event definition may sit on this event position.
 *
 * <p>The catalogue lists five event tags and ten event definitions, but nobody places a "boundary
 * event" and then picks a type - they place a <em>timer boundary event</em>. Those concrete shapes
 * are the position crossed with the definition, and roughly fifty of them are legal. Writing all
 * fifty out by hand would be long and would get some wrong; declaring the rules and deriving the
 * shapes cannot produce a combination the matrix does not allow.
 *
 * @param context     where the position sits. A start event accepts a very different set inside an
 *                    event sub-process than at the top of a process, so context is part of the rule
 *                    rather than a footnote on it.
 * @param definitionShapeId the event definition shape id, or null for a plain event with no trigger
 * @param interrupting whether {@code cancelActivity} is a real choice here
 * @param requires    extra conditions beyond the pairing itself, e.g. a cancel boundary event needs
 *                    a transaction as its host
 */
public record EventComposition(String positionShapeId,
                               String context,
                               String definitionShapeId,
                               String interrupting,
                               List<String> requires) {

  /** Anywhere the position is valid. */
  public static final String ANY = "ANY";

  /** Directly in a process or sub-process, not in an event sub-process. */
  public static final String TOP_LEVEL = "TOP_LEVEL";

  /** Inside an event sub-process, which changes what a start event may carry. */
  public static final String EVENT_SUB_PROCESS = "EVENT_SUB_PROCESS";

  /** Both interrupting and non-interrupting forms exist, so this rule yields two shapes. */
  public static final String BOTH = "BOTH";

  /** Only the interrupting form is legal - an error boundary event cannot let its host continue. */
  public static final String INTERRUPTING_ONLY = "INTERRUPTING_ONLY";

  /** The distinction does not apply: nothing is being interrupted. */
  public static final String NOT_APPLICABLE = "NOT_APPLICABLE";

  public EventComposition {
    requires = requires == null ? List.of() : List.copyOf(requires);
  }

  public static EventComposition of(String positionShapeId, String context, String definitionShapeId,
                                    String interrupting) {
    return new EventComposition(positionShapeId, context, definitionShapeId, interrupting, List.of());
  }

  public static EventComposition of(String positionShapeId, String context, String definitionShapeId,
                                    String interrupting, String... requires) {
    return new EventComposition(positionShapeId, context, definitionShapeId, interrupting, List.of(requires));
  }

  /** A plain event carrying no definition at all - a bare start, throw or end. */
  public boolean plain() {
    return definitionShapeId == null;
  }
}
