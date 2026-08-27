package com.argus.properties.catalogue.model;

import java.util.List;

/**
 * A concrete event shape - the thing a modeller actually places on a canvas.
 *
 * <p>Derived from the legality matrix rather than declared, so the set cannot drift from the rules
 * and cannot contain a pairing the engine would reject.
 *
 * @param interrupting null when the distinction does not apply to this shape
 * @param xmlSketch    the nesting the shape serialises to, which is the thing people most often get
 *                     wrong when hand-editing
 */
public record EventShape(String id,
                         String name,
                         String positionShapeId,
                         String positionTag,
                         String context,
                         String definitionShapeId,
                         String definitionTag,
                         Boolean interrupting,
                         List<String> requires,
                         String summary,
                         String xmlSketch,
                         Behaviour behaviour) {

  public EventShape {
    requires = requires == null ? List.of() : List.copyOf(requires);
  }

  public EventShape withBehaviour(Behaviour behaviour) {
    return new EventShape(id, name, positionShapeId, positionTag, context, definitionShapeId,
        definitionTag, interrupting, requires, summary, xmlSketch, behaviour);
  }
}
