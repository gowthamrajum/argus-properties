package com.argus.properties.catalogue;

import com.argus.properties.catalogue.model.Behaviour;
import com.argus.properties.catalogue.model.CategoryEntry;
import com.argus.properties.catalogue.model.EventComposition;
import com.argus.properties.catalogue.model.EventShape;
import com.argus.properties.catalogue.model.PropertiesResponse;
import com.argus.properties.catalogue.model.PropertyUsage;
import com.argus.properties.catalogue.model.Property;
import com.argus.properties.catalogue.model.Shape;
import com.argus.properties.catalogue.model.ShapeSummary;
import com.argus.properties.catalogue.model.ShapesResponse;
import com.argus.properties.exception.UnknownElementException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Query and shaping over {@link ShapeCatalogue}.
 *
 * <p>Split from the catalogue so the catalogue stays a pure declaration plus lookup, and every
 * decision about filtering, ordering and tallying is in one place a test can drive without the
 * web layer.
 */
@Service
public class ShapeService {

  private final ShapeCatalogue catalogue;

  public ShapeService(ShapeCatalogue catalogue) {
    this.catalogue = catalogue;
  }

  /**
   * The shape list, optionally narrowed.
   *
   * <p>{@code q} matches id, name, tag and summary rather than name alone: people look for
   * "boundary", "camunda:assignee" and "bpmn:userTask" interchangeably, and a search that only
   * covers the display name fails all but the first.
   */
  public ShapesResponse shapes(String category, String query, Boolean executable) {
    String normalisedCategory = category == null ? null : category.toUpperCase(Locale.ROOT);
    if (normalisedCategory != null && categories().stream()
        .noneMatch(entry -> entry.category().equals(normalisedCategory))) {
      throw new UnknownElementException("No category '" + category
          + "'. GET /api/v1/categories lists them all.");
    }
    String normalisedQuery = StringUtils.hasText(query) ? query.toLowerCase(Locale.ROOT) : null;

    List<ShapeSummary> matched = catalogue.all().stream()
        .filter(shape -> normalisedCategory == null || shape.category().equals(normalisedCategory))
        .filter(shape -> executable == null || shape.executable() == executable)
        .filter(shape -> normalisedQuery == null || matches(shape, normalisedQuery))
        .map(shape -> ShapeSummary.of(shape, catalogue.effectiveProperties(shape).size()))
        .toList();

    return new ShapesResponse(matched.size(), tally(matched, ShapeSummary::category), matched);
  }

  public Shape shape(String id) {
    return catalogue.shape(id);
  }

  /**
   * The properties of one shape.
   *
   * @param own       true drops everything inherited, leaving only what makes this shape different
   * @param namespace {@code bpmn}, {@code camunda} or {@code bpmndi}; the useful cut when the
   *                  question is "what is vendor-specific here?"
   */
  public PropertiesResponse properties(String id, boolean own, String namespace) {
    Shape shape = catalogue.shape(id);
    List<Property> properties = (own ? shape.properties() : catalogue.effectiveProperties(shape)).stream()
        .filter(property -> namespace == null
            || property.namespace().equalsIgnoreCase(namespace))
        .toList();

    return new PropertiesResponse(shape.id(), shape.name(), shape.tag(), properties.size(),
        tally(properties, Property::namespace), tally(properties, Property::kind), properties);
  }

  /** One named property of one shape, e.g. {@code /shapes/user-task/properties/camunda:assignee}. */
  public Property property(String shapeId, String propertyName) {
    Shape shape = catalogue.shape(shapeId);
    return catalogue.effectiveProperties(shape).stream()
        .filter(property -> property.name().equalsIgnoreCase(propertyName))
        .findFirst()
        .orElseThrow(() -> new UnknownElementException("Shape '" + shapeId + "' has no property '"
            + propertyName + "'. GET /api/v1/shapes/" + shapeId + "/properties lists them all."));
  }

  /**
   * The run-time behaviour of one shape.
   *
   * <p>A shape with no profile yet is a 404 rather than an empty body: "we have not catalogued this
   * one" and "this shape does nothing" are different answers, and an empty body reads as the second.
   */
  public Behaviour behaviour(String id) {
    Shape shape = shape(id);
    if (shape.behaviour() == null) {
      throw new UnknownElementException(("Shape '%s' has no behaviour profile yet. Covered so far: "
          + "%s.").formatted(id, String.join(", ", catalogue.behaviours().keySet())));
    }
    return shape.behaviour();
  }

  /**
   * Concrete event shapes, optionally narrowed. Filters combine, so ?position=boundary-event
   * &interrupting=false is the non-interrupting boundary family.
   */
  public List<EventShape> eventShapes(String position, String definition, Boolean interrupting) {
    return catalogue.eventShapes().stream()
        .filter(shape -> position == null || position.equalsIgnoreCase(shape.positionShapeId()))
        .filter(shape -> definition == null || definition.equalsIgnoreCase(shape.definitionShapeId()))
        .filter(shape -> interrupting == null || interrupting.equals(shape.interrupting()))
        .toList();
  }

  public EventShape eventShape(String id) {
    return catalogue.eventShape(id);
  }

  /**
   * Whether a position and a definition may be combined - the question the matrix exists to answer.
   * A rejection says what the position does accept, because "no" on its own leaves the caller
   * guessing at what to try next.
   */
  public LegalityAnswer check(String position, String definition, String context) {
    List<EventShape> matches = catalogue.eventShapes().stream()
        .filter(shape -> position.equalsIgnoreCase(shape.positionShapeId()))
        .filter(shape -> definition == null
            ? shape.definitionShapeId() == null
            : definition.equalsIgnoreCase(shape.definitionShapeId()))
        .filter(shape -> context == null || context.equalsIgnoreCase(shape.context())
            || EventComposition.ANY.equals(shape.context()))
        .toList();

    if (!matches.isEmpty()) {
      return new LegalityAnswer(true, position, definition, matches.stream().map(EventShape::id).toList(),
          matches.getFirst().requires(), null);
    }

    List<String> accepted = catalogue.eventShapes().stream()
        .filter(shape -> position.equalsIgnoreCase(shape.positionShapeId()))
        .map(EventShape::definitionShapeId)
        .map(id -> id == null ? "(none)" : id)
        .distinct().sorted().toList();

    return new LegalityAnswer(false, position, definition, List.of(), List.of(),
        accepted.isEmpty()
            ? "'%s' is not an event position.".formatted(position)
            : "%s does not accept %s. It accepts: %s.".formatted(position,
                definition == null ? "a plain event" : definition, String.join(", ", accepted)));
  }

  /** @param shapeIds the concrete shapes this pairing produces - two when it has both forms */
  public record LegalityAnswer(boolean legal,
                               String position,
                               String definition,
                               List<String> shapeIds,
                               List<String> requires,
                               String reason) {
  }

  /**
   * The catalogue indexed by property rather than by shape.
   *
   * <p>Built by walking every shape's effective properties, so a property contributed by a group
   * is credited to each shape that inherits it - which is the whole point. Asking where execution
   * listeners apply should not require knowing that they live in a group.
   */
  public List<PropertyUsage> propertyIndex(String kind, String namespace, String query) {
    String normalisedQuery = StringUtils.hasText(query) ? query.toLowerCase(Locale.ROOT) : null;

    return usagesByName().values().stream()
        .filter(usage -> kind == null || usage.kind().equalsIgnoreCase(kind))
        .filter(usage -> namespace == null || usage.namespace().equalsIgnoreCase(namespace))
        .filter(usage -> normalisedQuery == null
            || usage.name().toLowerCase(Locale.ROOT).contains(normalisedQuery)
            || usage.label().toLowerCase(Locale.ROOT).contains(normalisedQuery))
        // Summary only: the occurrences make the listing an order of magnitude larger.
        .map(usage -> new PropertyUsage(usage.name(), usage.label(), usage.namespace(),
            usage.kind(), usage.shapeCount(), List.of()))
        .toList();
  }

  public PropertyUsage propertyUsage(String name) {
    PropertyUsage usage = usagesByName().get(name);
    if (usage == null) {
      throw new UnknownElementException("No property named '" + name
          + "' anywhere in the catalogue. GET /api/v1/properties lists them all.");
    }
    return usage;
  }

  private Map<String, PropertyUsage> usagesByName() {
    Map<String, List<PropertyUsage.Occurrence>> occurrences = new LinkedHashMap<>();
    Map<String, Property> firstSeen = new LinkedHashMap<>();

    for (Shape shape : catalogue.all()) {
      for (Property property : catalogue.effectiveProperties(shape)) {
        firstSeen.putIfAbsent(property.name(), property);
        occurrences.computeIfAbsent(property.name(), key -> new ArrayList<>())
            .add(new PropertyUsage.Occurrence(shape.id(), shape.name(), shape.category(),
                property.inheritedFrom(), property.description()));
      }
    }

    Map<String, PropertyUsage> index = new LinkedHashMap<>();
    occurrences.forEach((name, list) -> {
      Property property = firstSeen.get(name);
      index.put(name, new PropertyUsage(name, property.label(), property.namespace(),
          property.kind(), list.size(), List.copyOf(list)));
    });
    return index;
  }

  public List<CategoryEntry> categories() {
    Map<String, List<String>> byCategory = catalogue.all().stream().collect(Collectors.groupingBy(
        Shape::category, LinkedHashMap::new, Collectors.mapping(Shape::id, Collectors.toList())));

    return byCategory.entrySet().stream()
        .map(entry -> new CategoryEntry(entry.getKey(), Shape.labelFor(entry.getKey()),
            entry.getValue().size(), entry.getValue()))
        .toList();
  }

  private static boolean matches(Shape shape, String query) {
    return contains(shape.id(), query)
        || contains(shape.name(), query)
        || contains(shape.tag(), query)
        || contains(shape.summary(), query);
  }

  private static boolean contains(String value, String query) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(query);
  }

  private static <T> Map<String, Integer> tally(List<T> items, Function<T, String> key) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    items.forEach(item -> counts.merge(key.apply(item), 1, Integer::sum));
    return counts;
  }
}
