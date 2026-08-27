package com.argus.properties.catalogue.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One shape a modeller can place on a BPMN canvas.
 *
 * <p>A "shape" here is what a person picks from the palette, which is not the same thing as an XML
 * tag. A Message Boundary Event and a Timer Boundary Event are two shapes but one tag
 * ({@code bpmn:boundaryEvent}) distinguished by a child event definition; an Event Sub Process is
 * {@code bpmn:subProcess} distinguished by an attribute. So {@link #id} - not {@link #tag} - is
 * the key, and event definitions are catalogued as shapes in their own right because that is where
 * their properties live.
 *
 * @param id          stable kebab-case identifier used in URLs, e.g. {@code user-task}
 * @param tag         the qualified XML element name, e.g. {@code bpmn:userTask}
 * @param executable  whether the Camunda 7 / Fluxnova engine acts on this shape. False means a
 *                    modeller will happily draw it and the engine will ignore it - the single most
 *                    useful thing to know before building a model around one.
 * @param properties  only what this shape declares itself; inherited properties come from
 *                    {@link #inherits}
 * @param constraints rules the XML must satisfy that are not expressible as a property, e.g.
 *                    "no incoming sequence flow"
 */
public record Shape(String id,
                    String name,
                    String tag,
                    String category,
                    String summary,
                    boolean executable,
                    List<String> inherits,
                    List<Property> properties,
                    Notation notation,
                    List<String> constraints,
                    String xmlExample,
                    Behaviour behaviour) {

  // Categories - the palette groups, plus EVENT_DEFINITION which is a palette group only
  // implicitly (choosing a "message start event" is choosing an event definition).
  public static final String CONTAINER = "CONTAINER";
  public static final String ACTIVITY = "ACTIVITY";
  public static final String GATEWAY = "GATEWAY";
  public static final String EVENT = "EVENT";
  public static final String EVENT_DEFINITION = "EVENT_DEFINITION";
  public static final String DATA = "DATA";
  public static final String ARTIFACT = "ARTIFACT";
  public static final String CONNECTION = "CONNECTION";

  /**
   * How a category is written for people.
   *
   * <p>Plural, because these name families rather than instances - "Gateways" is the group a
   * gateway belongs to. Declared rather than derived so DATA does not become "Datas" and
   * EVENT_DEFINITION does not become "Event_definitions".
   */
  public static String labelFor(String category) {
    return switch (category) {
      case CONTAINER -> "Containers";
      case ACTIVITY -> "Activities";
      case GATEWAY -> "Gateways";
      case EVENT -> "Events";
      case EVENT_DEFINITION -> "Event definitions";
      case DATA -> "Data";
      case ARTIFACT -> "Artifacts";
      case CONNECTION -> "Connections";
      default -> category;
    };
  }

  public Shape {
    inherits = inherits == null ? List.of() : List.copyOf(inherits);
    properties = properties == null ? List.of() : List.copyOf(properties);
    constraints = constraints == null ? List.of() : List.copyOf(constraints);
  }

  /**
   * Attaches a behaviour profile. Behaviours are declared separately from shapes so a shape stays
   * one readable block: the property list is already long, and what a shape <em>does</em> is a
   * different subject from what you can set on it.
   */
  public Shape withBehaviour(Behaviour behaviour) {
    return new Shape(id, name, tag, category, summary, executable, inherits, properties, notation,
        constraints, xmlExample, behaviour);
  }

  public static Builder of(String id, String name, String tag, String category) {
    return new Builder(id, name, tag, category);
  }

  /**
   * Eleven positional arguments would be unreadable in a catalogue this long, and most shapes set
   * only half of them. The builder keeps each declaration to the fields that actually vary.
   */
  public static final class Builder {

    private final String id;
    private final String name;
    private final String tag;
    private final String category;
    private String summary;
    private boolean executable = true;
    private List<String> inherits = List.of();
    private final List<Property> properties = new ArrayList<>();
    private Notation notation;
    private List<String> constraints = List.of();
    private String xmlExample;
    private Behaviour behaviour;

    private Builder(String id, String name, String tag, String category) {
      this.id = id;
      this.name = name;
      this.tag = tag;
      this.category = category;
    }

    public Builder summary(String summary) {
      this.summary = summary;
      return this;
    }

    /** Marks a shape the engine ignores; {@code why} is appended to the summary by the catalogue. */
    public Builder notExecutable() {
      this.executable = false;
      return this;
    }

    public Builder inherits(String... groupIds) {
      this.inherits = List.of(groupIds);
      return this;
    }

    public Builder notation(Notation notation) {
      this.notation = notation;
      return this;
    }

    public Builder properties(Property... declared) {
      this.properties.addAll(List.of(declared));
      return this;
    }

    public Builder constraints(String... constraints) {
      this.constraints = List.of(constraints);
      return this;
    }

    public Builder example(String xmlExample) {
      this.xmlExample = xmlExample;
      return this;
    }

    public Builder behaviour(Behaviour behaviour) {
      this.behaviour = behaviour;
      return this;
    }

    public Shape build() {
      return new Shape(id, name, tag, category, summary, executable, inherits, properties,
          notation, constraints, xmlExample, behaviour);
    }
  }
}
