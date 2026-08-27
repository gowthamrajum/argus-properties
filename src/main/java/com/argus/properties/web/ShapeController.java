package com.argus.properties.web;

import com.argus.properties.catalogue.BpmnPreview;
import com.argus.properties.catalogue.ShapeCatalogue;
import com.argus.properties.catalogue.ShapeService;
import com.argus.properties.catalogue.model.Behaviour;
import com.argus.properties.catalogue.model.CategoryEntry;
import com.argus.properties.catalogue.model.EventShape;
import com.argus.properties.catalogue.model.Concept;
import com.argus.properties.catalogue.model.ListenerType;
import com.argus.properties.catalogue.model.Notation;
import com.argus.properties.catalogue.model.PropertiesResponse;
import com.argus.properties.catalogue.model.Property;
import com.argus.properties.catalogue.model.PropertyGroup;
import com.argus.properties.catalogue.model.PropertyUsage;
import com.argus.properties.catalogue.model.Shape;
import com.argus.properties.catalogue.model.ShapesResponse;
import com.argus.properties.web.dto.Capability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The shape catalogue as a REST resource.
 *
 * <p>Modelled as a resource tree rather than a set of queries: a shape is a thing, its properties
 * are a sub-resource of it, and one property is a sub-resource of those. So a caller who has a
 * shape id can reach everything about it by appending path segments, and never has to construct a
 * query it did not already know existed.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "BPMN Shapes", description = "Every shape a Camunda 7 / Fluxnova BPMN file can contain")
public class ShapeController {

  private final ShapeService service;
  private final ShapeCatalogue catalogue;
  private final BpmnPreview preview;

  public ShapeController(ShapeService service, ShapeCatalogue catalogue, BpmnPreview preview) {
    this.service = service;
    this.catalogue = catalogue;
    this.preview = preview;
  }

  @Operation(summary = "List every capability this service exposes",
      description = "Start here. Each capability is its own path.")
  @GetMapping(path = "/capabilities", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<Capability> capabilities() {
    return List.of(
        new Capability("shapes", "Shapes",
            "Every shape in the catalogue, filterable by category, free text or engine support.",
            "GET", "/api/v1/shapes"),
        new Capability("shape", "Shape",
            "One shape in full: its tag, notation, constraints, own properties and example XML.",
            "GET", "/api/v1/shapes/{id}"),
        new Capability("properties", "Properties",
            "The effective property set of one shape - inherited plus own - with each property's "
                + "type, default, allowed values and where it lands in the XML.",
            "GET", "/api/v1/shapes/{id}/properties"),
        new Capability("property", "Property",
            "One named property of one shape, e.g. camunda:assignee.",
            "GET", "/api/v1/shapes/{id}/properties/{name}"),
        new Capability("preview", "Preview",
            "A real .bpmn document containing just this shape, laid out at its documented default "
                + "bounds. Render it with bpmn-js, or save it and open it in Camunda Modeler.",
            "GET", "/api/v1/shapes/{id}/preview"),
        new Capability("notation", "Notation",
            "How the shape is drawn and the default bounds a modeller creates it at - what a DI "
                + "generator needs.",
            "GET", "/api/v1/shapes/{id}/notation"),
        new Capability("concepts", "Concepts",
            "The vocabulary in plain language - what a shape is, what a property is, why some "
                + "properties are Camunda-specific. Start here if BPMN is new to you.",
            "GET", "/api/v1/concepts"),
        new Capability("behaviour", "Behaviour",
            "What the shape does when a token reaches it: every outcome execution can end in, "
                + "where the transaction boundary sits, and what happens to a technical failure.",
            "GET", "/api/v1/shapes/{id}/behaviour"),
        new Capability("event-shapes", "Event Shapes",
            "The concrete event shapes a modeller places - a position crossed with a definition, "
                + "derived from the legality matrix. About fifty from five tags.",
            "GET", "/api/v1/event-shapes"),
        new Capability("event-shape-check", "Event Legality Check",
            "Whether a given position and definition may be combined, and what the position "
                + "accepts if not.",
            "GET", "/api/v1/event-shapes/check"),
        new Capability("listeners", "Listeners",
            "The two listener families and every event each one offers - start and end for "
                + "execution listeners, create/assignment/update/complete/delete/timeout for task "
                + "listeners, with what each one fires on and the ones that surprise people.",
            "GET", "/api/v1/listeners"),
        new Capability("properties-index", "Property Index",
            "The catalogue read along the other axis: every distinct property, and every shape it "
                + "applies to. Answers \"which shapes support task listeners?\" in one call.",
            "GET", "/api/v1/properties"),
        new Capability("categories", "Categories",
            "The palette groups, with the shape ids in each.",
            "GET", "/api/v1/categories"),
        new Capability("property-groups", "Property Groups",
            "The shared property sets shapes inherit, declared once.",
            "GET", "/api/v1/property-groups"));
  }

  @Operation(summary = "Every shape in the catalogue",
      description = "Summaries only - fetch a shape by id for its properties. Filters combine: "
          + "?category=GATEWAY&executable=false narrows to gateways the engine ignores.")
  @GetMapping(path = "/shapes", produces = MediaType.APPLICATION_JSON_VALUE)
  public ShapesResponse shapes(
      @Parameter(description = "Palette group, e.g. TASK. Case-insensitive.")
      @RequestParam(required = false) String category,
      @Parameter(description = "Free text over id, name, tag and summary.")
      @RequestParam(required = false) String q,
      @Parameter(description = "true for shapes the Camunda 7 / Fluxnova engine acts on, false for "
          + "the modelling-only ones.")
      @RequestParam(required = false) Boolean executable) {
    return service.shapes(category, q, executable);
  }

  @Operation(summary = "One shape in full",
      description = "Includes only the properties the shape declares itself; the inherited ones "
          + "are at /shapes/{id}/properties.")
  @GetMapping(path = "/shapes/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Shape shape(@PathVariable String id) {
    return service.shape(id);
  }

  @Operation(summary = "The effective properties of one shape",
      description = "Inherited properties first, each stamped with the group it came from, then "
          + "the shape's own. Use ?own=true for just the distinguishing ones, or "
          + "?namespace=camunda for just the vendor extensions.")
  @GetMapping(path = "/shapes/{id}/properties", produces = MediaType.APPLICATION_JSON_VALUE)
  public PropertiesResponse properties(
      @PathVariable String id,
      @Parameter(description = "Drop inherited properties, leaving only what this shape declares.")
      @RequestParam(defaultValue = "false") boolean own,
      @Parameter(description = "bpmn, camunda or bpmndi.")
      @RequestParam(required = false) String namespace) {
    return service.properties(id, own, namespace);
  }

  @Operation(summary = "One named property of one shape",
      description = "The name includes its prefix, e.g. camunda:assignee or isExecutable.")
  @GetMapping(path = "/shapes/{id}/properties/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Property property(@PathVariable String id, @PathVariable String name) {
    return service.property(id, name);
  }

  @Operation(summary = "A real .bpmn file containing just this shape",
      description = "Genuine BPMN, not an approximation: hand it to bpmn-js and you get exactly "
          + "what bpmn.io draws. Geometry comes from the shape's own notation, so the picture and "
          + "the documented default bounds cannot drift. Shapes that are never drawn - a process, "
          + "a lane set, a data object declaration - answer 404 with the reason.")
  @GetMapping(path = "/shapes/{id}/preview", produces = MediaType.APPLICATION_XML_VALUE)
  public String preview(@PathVariable String id) {
    return preview.xmlFor(service.shape(id));
  }

  @Operation(summary = "How one shape is drawn",
      description = "Default bounds, DI element and markers - what you need to emit valid BPMN DI "
          + "for this shape.")
  @GetMapping(path = "/shapes/{id}/notation", produces = MediaType.APPLICATION_JSON_VALUE)
  public Notation notation(@PathVariable String id) {
    return service.shape(id).notation();
  }

  @Operation(summary = "The vocabulary, in plain language",
      description = "Short, jargon-free explanations of shape, property, namespace, inheritance "
          + "and the rest, each with a concrete example.")
  @GetMapping(path = "/concepts", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<Concept> concepts() {
    return catalogue.concepts();
  }

  @Operation(summary = "One concept")
  @GetMapping(path = "/concepts/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Concept concept(@PathVariable String id) {
    return catalogue.concept(id);
  }

  @Operation(summary = "What one shape does at run time",
      description = "Every way a token can leave the shape - or fail to - plus the save point that "
          + "decides how far a failure unwinds, and the retry behaviour. Covers tasks and gateways; "
          + "other shapes return 404 until catalogued.")
  @GetMapping(path = "/shapes/{id}/behaviour", produces = MediaType.APPLICATION_JSON_VALUE)
  public Behaviour behaviour(@PathVariable String id) {
    return service.behaviour(id);
  }

  @Operation(summary = "Every concrete event shape",
      description = "A position crossed with an event definition - timer boundary event, "
          + "non-interrupting message boundary event, error end event. Derived from the legality "
          + "matrix rather than declared, so it cannot contain a pairing the engine would reject.")
  @GetMapping(path = "/event-shapes", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<EventShape> eventShapes(
      @Parameter(description = "Position shape id, e.g. boundary-event.")
      @RequestParam(required = false) String position,
      @Parameter(description = "Definition shape id, e.g. timer-event-definition.")
      @RequestParam(required = false) String definition,
      @Parameter(description = "true for interrupting forms, false for non-interrupting.")
      @RequestParam(required = false) Boolean interrupting) {
    return service.eventShapes(position, definition, interrupting);
  }

  @Operation(summary = "Whether a position and definition may be combined",
      description = "Answers the question the matrix exists for. A rejection lists what the "
          + "position does accept.")
  @GetMapping(path = "/event-shapes/check", produces = MediaType.APPLICATION_JSON_VALUE)
  public ShapeService.LegalityAnswer checkEventShape(
      @RequestParam String position,
      @Parameter(description = "Omit for a plain event with no definition.")
      @RequestParam(required = false) String definition,
      @Parameter(description = "TOP_LEVEL or EVENT_SUB_PROCESS; only affects start events.")
      @RequestParam(required = false) String context) {
    return service.check(position, definition, context);
  }

  @Operation(summary = "One concrete event shape, with its composed behaviour")
  @GetMapping(path = "/event-shapes/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public EventShape eventShape(@PathVariable String id) {
    return service.eventShape(id);
  }

  @Operation(summary = "The listener families and their events",
      description = "\"Add an execution listener\" is not an instruction anyone can follow - the "
          + "useful question is when. This lists every event value, the exact moment it fires, "
          + "what it is normally used for, and the caveat that catches people out.")
  @GetMapping(path = "/listeners", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<ListenerType> listeners() {
    return catalogue.listenerTypes();
  }

  @Operation(summary = "One listener family")
  @GetMapping(path = "/listeners/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ListenerType listener(@PathVariable String id) {
    return catalogue.listenerType(id);
  }

  @Operation(summary = "Every distinct property in the catalogue",
      description = "Indexed by property rather than by shape. Filter by kind to find the "
          + "extension elements - listeners, input/output mapping, form data - or by namespace to "
          + "separate standard BPMN from Camunda's additions.")
  @GetMapping(path = "/properties", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<PropertyUsage> properties(
      @Parameter(description = "ATTRIBUTE, CHILD_ELEMENT, EXTENSION_ELEMENT or DI_ATTRIBUTE.")
      @RequestParam(required = false) String kind,
      @Parameter(description = "bpmn, camunda or bpmndi.")
      @RequestParam(required = false) String namespace,
      @Parameter(description = "Free text over the XML name and the label.")
      @RequestParam(required = false) String q) {
    return service.propertyIndex(kind, namespace, q);
  }

  @Operation(summary = "One property, and every shape it applies to",
      description = "The description is carried per shape, because the same property does not "
          + "always mean quite the same thing - an execution listener on a sequence flow fires on "
          + "take, on a flow node it fires on start or end.")
  @GetMapping(path = "/properties/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
  public PropertyUsage property(@PathVariable String name) {
    return service.propertyUsage(name);
  }

  @Operation(summary = "The palette groups")
  @GetMapping(path = "/categories", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<CategoryEntry> categories() {
    return service.categories();
  }

  @Operation(summary = "The shared property sets shapes inherit",
      description = "Declared once and referenced by id from each shape's 'inherits' list.")
  @GetMapping(path = "/property-groups", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<PropertyGroup> propertyGroups() {
    return catalogue.groups();
  }

  @Operation(summary = "One shared property set")
  @GetMapping(path = "/property-groups/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public PropertyGroup propertyGroup(@PathVariable String id) {
    return catalogue.group(id);
  }
}
