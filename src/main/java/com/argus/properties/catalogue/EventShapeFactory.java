package com.argus.properties.catalogue;

import com.argus.properties.catalogue.model.EventComposition;
import com.argus.properties.catalogue.model.EventShape;
import com.argus.properties.catalogue.model.Shape;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Expands the legality matrix into the concrete event shapes a modeller places.
 *
 * <p>Nothing here decides what is legal - that is entirely {@link EventCompositionRules}. This only
 * turns each rule into one shape, or two where the interrupting axis applies. Deriving rather than
 * declaring is what guarantees the set stays consistent with the rules: there is no way to add a
 * shape the matrix does not permit, and no way to change the matrix without the shape list moving
 * with it.
 */
final class EventShapeFactory {

  private EventShapeFactory() {
  }

  static List<EventShape> derive(List<EventComposition> rules, Map<String, Shape> shapesById) {
    List<EventShape> derived = new ArrayList<>();
    for (EventComposition rule : rules) {
      Shape position = shapesById.get(rule.positionShapeId());
      Shape definition = rule.plain() ? null : shapesById.get(rule.definitionShapeId());
      if (position == null || (!rule.plain() && definition == null)) {
        throw new IllegalStateException("Composition rule references an unknown shape: " + rule);
      }

      switch (rule.interrupting()) {
        case EventComposition.BOTH -> {
          derived.add(build(rule, position, definition, Boolean.TRUE));
          derived.add(build(rule, position, definition, Boolean.FALSE));
        }
        case EventComposition.INTERRUPTING_ONLY -> derived.add(build(rule, position, definition, Boolean.TRUE));
        default -> derived.add(build(rule, position, definition, null));
      }
    }

    Map<String, EventShape> byId = new LinkedHashMap<>();
    derived.forEach(shape -> {
      if (byId.putIfAbsent(shape.id(), shape) != null) {
        throw new IllegalStateException("Two composition rules produce the same shape id: " + shape.id());
      }
    });
    return List.copyOf(byId.values());
  }

  private static EventShape build(EventComposition rule, Shape position, Shape definition,
                                  Boolean interrupting) {
    String id = idOf(rule, definition, interrupting);
    String name = nameOf(rule, definition, interrupting);
    return new EventShape(id, name, position.id(), position.tag(), rule.context(),
        rule.plain() ? null : definition.id(),
        rule.plain() ? null : definition.tag(),
        interrupting, rule.requires(), summaryOf(rule, position, definition, interrupting),
        sketchOf(position, definition, interrupting), null);
  }

  /**
   * Ids read the way people say the shapes out loud - "timer boundary event", "non-interrupting
   * message boundary event" - so a caller can guess one without consulting the list.
   */
  private static String idOf(EventComposition rule, Shape definition, Boolean interrupting) {
    StringBuilder id = new StringBuilder();
    if (Boolean.FALSE.equals(interrupting)) {
      id.append("non-interrupting-");
    }
    id.append(rule.plain() ? "none" : trigger(definition)).append('-');
    if (EventComposition.EVENT_SUB_PROCESS.equals(rule.context())) {
      id.append("event-sub-process-");
    }
    return id.append(rule.positionShapeId()).toString();
  }

  private static String nameOf(EventComposition rule, Shape definition, Boolean interrupting) {
    StringBuilder name = new StringBuilder();
    if (Boolean.FALSE.equals(interrupting)) {
      name.append("Non-interrupting ");
    }
    if (!rule.plain()) {
      name.append(capitalise(trigger(definition))).append(' ');
    }
    if (EventComposition.EVENT_SUB_PROCESS.equals(rule.context())) {
      name.append("Event Sub-Process ");
    }
    return name.append(positionName(rule.positionShapeId())).toString();
  }

  private static String summaryOf(EventComposition rule, Shape position, Shape definition,
                                  Boolean interrupting) {
    String trigger = rule.plain() ? "no trigger of its own" : trigger(definition);
    String base = "%s carrying %s.".formatted(positionName(rule.positionShapeId()),
        rule.plain() ? trigger : "a " + trigger + " event definition");
    if (Boolean.TRUE.equals(interrupting)) {
      return base + " Interrupting: firing cancels what it is attached to.";
    }
    if (Boolean.FALSE.equals(interrupting)) {
      return base + " Non-interrupting: firing spawns a parallel token and leaves the host running.";
    }
    return base;
  }

  /** The nesting the shape serialises to - the part people get wrong when hand-editing XML. */
  private static String sketchOf(Shape position, Shape definition, Boolean interrupting) {
    String attributes = Boolean.FALSE.equals(interrupting) ? " cancelActivity=\"false\"" : "";
    if (definition == null) {
      return "<%s id=\"...\"%s />".formatted(position.tag(), attributes);
    }
    return "<%s id=\"...\"%s>\n  <%s />\n</%s>"
        .formatted(position.tag(), attributes, definition.tag(), position.tag());
  }

  /** "timer-event-definition" to "timer". */
  private static String trigger(Shape definition) {
    return definition.id().replace("-event-definition", "");
  }

  private static String positionName(String positionShapeId) {
    return switch (positionShapeId) {
      case "start-event" -> "Start Event";
      case "intermediate-catch-event" -> "Intermediate Catch Event";
      case "intermediate-throw-event" -> "Intermediate Throw Event";
      case "boundary-event" -> "Boundary Event";
      case "end-event" -> "End Event";
      default -> positionShapeId;
    };
  }

  private static String capitalise(String value) {
    return value.isEmpty() ? value
        : Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
  }
}
