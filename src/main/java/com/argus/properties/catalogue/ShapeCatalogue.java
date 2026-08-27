package com.argus.properties.catalogue;

import com.argus.properties.catalogue.model.Property;
import com.argus.properties.catalogue.model.Behaviour;
import com.argus.properties.catalogue.model.EventComposition;
import com.argus.properties.catalogue.model.EventShape;
import com.argus.properties.catalogue.model.Outcome;
import com.argus.properties.catalogue.model.PropertyGroup;
import com.argus.properties.catalogue.model.Concept;
import com.argus.properties.catalogue.model.ListenerType;
import com.argus.properties.catalogue.model.PropertyExamples;
import com.argus.properties.catalogue.model.PropertyLabels;
import com.argus.properties.catalogue.model.Shape;
import com.argus.properties.exception.UnknownElementException;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Every BPMN shape this service knows about, assembled and indexed once at startup.
 *
 * <p>Declared in Java rather than loaded from a resource file for the same reason
 * argus-backend declares its rules that way: the catalogue is the product here, so it should be
 * reviewable as code, diffable between releases, and wrong in ways the compiler and a test can
 * catch. A JSON blob would move every one of those checks to runtime.
 *
 * <p>The declarations live in one class per family - {@link TaskShapes}, {@link EventShapes} and
 * so on - because a single file listing fifty shapes with their properties is not something
 * anyone reviews carefully.
 */
@Component
public class ShapeCatalogue {

  /** Outcome ids a behaviour may declare. Anything else is a typo. */
  private static final Set<String> OUTCOME_IDS = Set.of(
      Outcome.COMPLETED, Outcome.WAITING, Outcome.BPMN_ERROR, Outcome.INCIDENT,
      Outcome.ROLLBACK, Outcome.STUCK, Outcome.UNSUPPORTED);

  private static final Set<String> EXECUTION_KINDS = Set.of(
      Behaviour.SYNCHRONOUS, Behaviour.WAIT_STATE, Behaviour.ROUTING, Behaviour.PASS_THROUGH,
      Behaviour.IMPLEMENTATION_DEPENDENT);

  private static final Set<String> SAVE_POINTS = Set.of(
      Behaviour.SAVE_POINT_ALWAYS, Behaviour.SAVE_POINT_ON_ASYNC, Behaviour.SAVE_POINT_NEVER,
      Behaviour.SAVE_POINT_IMPLEMENTATION_DEPENDENT);

  private final List<EventComposition> compositionRules;
  private final Map<String, EventShape> eventShapesById;
  private final Map<String, Shape> shapesById;
  private final Map<String, PropertyGroup> groupsById;
  private final Map<String, Behaviour> behavioursById;

  public ShapeCatalogue() {
    List<Shape> shapes = new ArrayList<>();
    shapes.addAll(ContainerShapes.all());
    shapes.addAll(ActivityShapes.all());
    shapes.addAll(GatewayShapes.all());
    shapes.addAll(EventShapes.all());
    shapes.addAll(EventDefinitionShapes.all());
    shapes.addAll(DataAndArtifactShapes.all());
    shapes.addAll(ConnectionShapes.all());

    // Behaviours are declared per family alongside the shapes and stitched on here, so a shape
    // declaration stays about configuration and a behaviour declaration stays about execution.
    Map<String, Behaviour> behaviours = new LinkedHashMap<>();
    behaviours.putAll(TaskBehaviours.all());
    behaviours.putAll(GatewayBehaviours.all());
    this.behavioursById = Map.copyOf(behaviours);

    this.shapesById = shapes.stream()
        .map(shape -> behaviours.containsKey(shape.id())
            ? shape.withBehaviour(behaviours.get(shape.id()))
            : shape)
        .collect(Collectors.toMap(Shape::id, shape -> shape, (a, b) -> a, LinkedHashMap::new));
    this.groupsById = PropertyGroups.all().stream().collect(Collectors.toMap(
        PropertyGroup::id, group -> group, (a, b) -> a, LinkedHashMap::new));

    // Assembled before validation so validate() can report every problem against a built index
    // rather than failing on the first duplicate key inside the collector.
    if (shapesById.size() != shapes.size()) {
      throw new IllegalStateException("Duplicate shape id in the catalogue: " + duplicateIds(shapes));
    }

    // Concrete event shapes are derived from the legality matrix, never declared, so the set cannot
    // contain a pairing the engine would reject and cannot drift from the rules.
    this.compositionRules = EventCompositionRules.all();
    this.eventShapesById = EventShapeFactory.derive(compositionRules, shapesById).stream()
        .map(eventShape -> eventShape.withBehaviour(EventBehaviours.compose(eventShape)))
        .collect(Collectors.toMap(EventShape::id, eventShape -> eventShape, (a, b) -> a, LinkedHashMap::new));
  }

  /**
   * Fails startup rather than serving a catalogue that contradicts itself.
   *
   * <p>A shape inheriting a group that does not exist would silently lose properties from
   * {@code /shapes/&#123;id&#125;/properties} - the caller sees a shorter list, not an error, and
   * has no way to know. That is exactly the class of bug worth paying a boot failure to avoid.
   */
  @PostConstruct
  void validate() {
    List<String> problems = new ArrayList<>();

    shapesById.values().forEach(shape -> {
      shape.inherits().stream()
          .filter(group -> !groupsById.containsKey(group))
          .forEach(group -> problems.add(shape.id() + " inherits unknown group '" + group + "'"));

      if (shape.summary() == null || shape.summary().isBlank()) {
        problems.add(shape.id() + " has no summary");
      }
      if (shape.notation() == null) {
        problems.add(shape.id() + " has no notation");
      }

      Set<String> seenGroups = new LinkedHashSet<>();
      shape.inherits().stream()
          .filter(group -> !seenGroups.add(group))
          .forEach(group -> problems.add(shape.id() + " inherits '" + group + "' twice"));

      Set<String> seen = new LinkedHashSet<>();
      shape.properties().stream()
          .map(Property::name)
          .filter(name -> !seen.add(name))
          .forEach(name -> problems.add(shape.id() + " declares '" + name + "' twice"));

      // Checked on the effective set, not just the declared one: a property that appears both in
      // a group and on the shape - or in two groups the shape inherits - is served twice, and a
      // caller reading the list has no way to tell which of the two descriptions is authoritative.
      Set<String> seenEffective = new LinkedHashSet<>();
      effectiveProperties(shape).stream()
          .map(Property::name)
          .filter(name -> !seenEffective.add(name))
          .forEach(name -> problems.add(shape.id() + " resolves '" + name + "' more than once"));

      shape.properties().stream()
          .filter(property -> property.description() == null || property.description().isBlank())
          .forEach(property -> problems.add(shape.id() + "." + property.name() + " has no description"));

      validateBehaviour(shape, problems);

      shape.properties().stream()
          .filter(property -> property.label() == null || property.label().isBlank())
          .forEach(property -> problems.add(shape.id() + "." + property.name() + " has no label"));
    });

    // An override for a property nothing declares any more is dead vocabulary: the label it was
    // protecting is gone, and the next rename will silently fall back to a derived name.
    Set<String> declared = new LinkedHashSet<>();
    shapesById.values().forEach(shape -> shape.properties().forEach(p -> declared.add(p.name())));
    groupsById.values().forEach(group -> group.properties().forEach(p -> declared.add(p.name())));
    PropertyLabels.overriddenNames().stream()
        .filter(name -> !declared.contains(name))
        .forEach(name -> problems.add("PropertyLabels overrides '" + name + "', which no shape declares"));
    PropertyExamples.exampledNames().stream()
        .filter(name -> !declared.contains(name))
        .forEach(name -> problems.add("PropertyExamples has '" + name + "', which no shape declares"));

    eventShapesById.values().forEach(eventShape -> {
      if (eventShape.behaviour() == null) {
        problems.add(eventShape.id() + " has no composed behaviour");
        return;
      }
      validateBehaviour(eventShape.id() + " (event shape)", eventShape.behaviour(), problems);
    });

    if (!problems.isEmpty()) {
      throw new IllegalStateException("Shape catalogue is inconsistent: " + String.join("; ", problems));
    }
  }

  /**
   * A behaviour profile is optional while coverage is still being filled in, but a profile that is
   * present has to be complete. A half-written one is worse than none: a caller cannot tell an
   * outcome that does not apply from an outcome nobody has got round to writing down.
   */
  private void validateBehaviour(Shape shape, List<String> problems) {
    if (shape.behaviour() != null) {
      validateBehaviour(shape.id() + ".behaviour", shape.behaviour(), problems);
    }
  }

  private void validateBehaviour(String at, Behaviour behaviour, List<String> problems) {
    if (!EXECUTION_KINDS.contains(behaviour.executionKind())) {
      problems.add(at + " has unknown executionKind '" + behaviour.executionKind() + "'");
    }
    if (!SAVE_POINTS.contains(behaviour.savePoint())) {
      problems.add(at + " has unknown savePoint '" + behaviour.savePoint() + "'");
    }
    if (behaviour.outcomes().isEmpty()) {
      problems.add(at + " declares no outcomes");
    }
    if (behaviour.retries() == null) {
      problems.add(at + " has no retry profile");
    }
    behaviour.outcomes().forEach(outcome -> {
      if (!OUTCOME_IDS.contains(outcome.id())) {
        problems.add(at + " has unknown outcome id '" + outcome.id() + "'");
      }
      if (outcome.trigger() == null || outcome.trigger().isBlank()) {
        problems.add(at + "." + outcome.id() + " has no trigger");
      }
      if (outcome.effect() == null || outcome.effect().isBlank()) {
        problems.add(at + "." + outcome.id() + " has no effect");
      }
      // A silent stall is the outcome people most need help with. Describing one without saying
      // how to get out of it is the least useful thing the catalogue could do.
      if (Outcome.STUCK.equals(outcome.id()) && (outcome.recovery() == null || outcome.recovery().isBlank())) {
        problems.add(at + ".STUCK has no recovery");
      }
    });
    // Every profile has to say how the token leaves on the happy path, or it is describing only
    // failure. UNSUPPORTED is the exception: nothing ever runs, so there is no happy path.
    boolean progresses = behaviour.outcomes().stream()
        .anyMatch(outcome -> Outcome.COMPLETED.equals(outcome.id())
            || Outcome.UNSUPPORTED.equals(outcome.id()));
    if (!progresses) {
      problems.add(at + " declares no COMPLETED outcome, so it never says how execution proceeds");
    }
  }

  /** Every legal position-plus-definition pairing, derived from the matrix. */
  public List<EventShape> eventShapes() {
    return List.copyOf(eventShapesById.values());
  }

  public EventShape eventShape(String id) {
    EventShape eventShape = eventShapesById.get(id);
    if (eventShape == null) {
      throw new UnknownElementException(("Unknown event shape '%s'. There are %d; list them at "
          + "/api/v1/event-shapes.").formatted(id, eventShapesById.size()));
    }
    return eventShape;
  }

  public List<EventComposition> compositionRules() {
    return List.copyOf(compositionRules);
  }

  /** Shapes whose run-time behaviour has been catalogued, in declaration order. */
  public Map<String, Behaviour> behaviours() {
    return behavioursById;
  }

  public List<Shape> all() {
    return List.copyOf(shapesById.values());
  }

  /** The vocabulary, in plain language - see {@link Concepts}. */
  public List<Concept> concepts() {
    return Concepts.all();
  }

  public Concept concept(String id) {
    return Concepts.all().stream()
        .filter(concept -> concept.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new UnknownElementException("No concept with id '" + id
            + "'. GET /api/v1/concepts lists them all."));
  }

  /** The listener families and the events each offers - see {@link Listeners}. */
  public List<ListenerType> listenerTypes() {
    return Listeners.all();
  }

  public ListenerType listenerType(String id) {
    return Listeners.all().stream()
        .filter(type -> type.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new UnknownElementException("No listener type '" + id
            + "'. GET /api/v1/listeners lists them all."));
  }

  public List<PropertyGroup> groups() {
    return List.copyOf(groupsById.values());
  }

  public Shape shape(String id) {
    Shape shape = shapesById.get(id);
    if (shape == null) {
      throw new UnknownElementException("No shape with id '" + id + "'. GET /api/v1/shapes lists them all.");
    }
    return shape;
  }

  public PropertyGroup group(String id) {
    PropertyGroup group = groupsById.get(id);
    if (group == null) {
      throw new UnknownElementException(
          "No property group with id '" + id + "'. GET /api/v1/property-groups lists them all.");
    }
    return group;
  }

  /**
   * The full property set of a shape: inherited first, in the order the shape lists its groups,
   * then the shape's own.
   *
   * <p>Inherited properties are stamped with their origin, so a caller can render "from every
   * flow node" separately from "specific to a user task" - or drop the inherited ones entirely -
   * without having to fetch the groups and diff them itself.
   */
  public List<Property> effectiveProperties(Shape shape) {
    List<Property> effective = new ArrayList<>();
    for (String groupId : shape.inherits()) {
      group(groupId).properties().forEach(property -> effective.add(property.declaredIn(groupId)));
    }
    effective.addAll(shape.properties());
    return List.copyOf(effective);
  }

  private static List<String> duplicateIds(List<Shape> shapes) {
    Set<String> seen = new LinkedHashSet<>();
    return shapes.stream().map(Shape::id).filter(id -> !seen.add(id)).distinct().toList();
  }
}
