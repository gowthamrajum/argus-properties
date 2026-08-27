package com.argus.properties.catalogue;

import com.argus.properties.catalogue.model.Shape;
import com.argus.properties.exception.UnknownElementException;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Builds a real, openable .bpmn document containing one shape.
 *
 * <p>The catalogue can describe a shape precisely and still leave you unsure what it looks like.
 * Rather than approximate the rendering in the UI - which is how you end up with a circle that is
 * subtly not a bpmn.io circle - the service emits genuine BPMN and lets bpmn-js draw it. The
 * geometry comes from the shape's own {@link com.argus.properties.catalogue.model.Notation}, so the
 * picture and the documented default bounds cannot drift apart.
 *
 * <p>A side effect worth having: every preview is a valid file. Save one and Camunda Modeler opens
 * it.
 *
 * <p>Some shapes cannot be drawn alone, and the generator supplies whatever context they need - a
 * boundary event gets a task to attach to, a message flow gets two pools, an event definition gets
 * a host event to sit inside. That context is the reason this is a generator rather than a
 * template.
 */
@Component
public class BpmnPreview {

  /** Structural or declaration-only elements: nothing to draw, with or without context. */
  private static final Set<String> NOT_DRAWN =
      Set.of("process", "collaboration", "lane-set", "data-object", "data-store");

  /**
   * Which event a definition is shown on. An event definition has no shape of its own - it is the
   * icon inside a circle - so the preview picks the host it most commonly appears on.
   */
  private static final Map<String, String> DEFINITION_HOST = Map.of(
      "message-event-definition", "bpmn:intermediateCatchEvent",
      "timer-event-definition", "bpmn:intermediateCatchEvent",
      "conditional-event-definition", "bpmn:intermediateCatchEvent",
      "signal-event-definition", "bpmn:intermediateCatchEvent",
      "link-event-definition", "bpmn:intermediateCatchEvent",
      "error-event-definition", "bpmn:endEvent",
      "escalation-event-definition", "bpmn:endEvent",
      "compensate-event-definition", "bpmn:endEvent",
      "cancel-event-definition", "bpmn:endEvent",
      "terminate-event-definition", "bpmn:endEvent");

  public boolean canPreview(Shape shape) {
    return !NOT_DRAWN.contains(shape.id());
  }

  public String xmlFor(Shape shape) {
    if (!canPreview(shape)) {
      throw new UnknownElementException("'" + shape.id() + "' is never drawn on a diagram, so it "
          + "has no preview. It is a declaration other shapes point at.");
    }
    return switch (shape.id()) {
      case "participant" -> pool();
      case "lane" -> laneInPool();
      case "boundary-event" -> boundaryOnTask();
      case "sequence-flow" -> sequenceFlow();
      case "message-flow" -> messageFlow();
      case "association" -> association();
      case "data-input-association" -> dataAssociation(true);
      case "data-output-association" -> dataAssociation(false);
      case "data-input" -> ioElement("bpmn:dataInput", "Order");
      case "data-output" -> ioElement("bpmn:dataOutput", "Receipt");
      default -> shape.category().equals(Shape.EVENT_DEFINITION)
          ? eventWithDefinition(shape)
          : standalone(shape);
    };
  }

  // ----------------------------------------------------------------- the common case

  /** One element, at its own documented default size. */
  private String standalone(Shape shape) {
    int width = orDefault(shape.notation().defaultWidth(), 100);
    int height = orDefault(shape.notation().defaultHeight(), 80);
    String tag = shape.tag();
    String attributes = extraAttributes(shape);

    // A collapsed sub-process is the recognisable palette form, and keeps every preview the same
    // size; expanded, it would be an empty box three times the size of everything else.
    boolean collapsible = tag.equals("bpmn:subProcess") || tag.equals("bpmn:transaction")
        || tag.equals("bpmn:adHocSubProcess");
    if (collapsible) {
      width = 100;
      height = 80;
    }

    String element = shape.category().equals(Shape.ARTIFACT)
        ? artifact(tag)
        : "<%s id=\"Element_1\" name=\"%s\"%s />".formatted(tag, shape.name(), attributes);
    String di = "<bpmndi:BPMNShape id=\"Element_1_di\" bpmnElement=\"Element_1\"%s>%s</bpmndi:BPMNShape>"
        .formatted(collapsible ? " isExpanded=\"false\"" : markerVisible(shape), bounds(160, 80, width, height));
    return document(process(element), di, "Preview_Process");
  }

  /**
   * Artifacts sit outside the flow-element hierarchy, so they have no name attribute at all - the
   * BPMN schema rejects one. A text annotation carries its words in a child element instead.
   */
  private String artifact(String tag) {
    return tag.equals("bpmn:textAnnotation")
        ? "<bpmn:textAnnotation id=\"Element_1\"><bpmn:text>SLA: two working days</bpmn:text></bpmn:textAnnotation>"
        : "<%s id=\"Element_1\" />".formatted(tag);
  }

  private String extraAttributes(Shape shape) {
    return switch (shape.id()) {
      case "event-sub-process" -> " triggeredByEvent=\"true\"";
      case "text-annotation" -> "";
      default -> "";
    };
  }

  /** The exclusive gateway's X is a diagram flag, so a preview without it looks like a plain diamond. */
  private String markerVisible(Shape shape) {
    return shape.id().equals("exclusive-gateway") ? " isMarkerVisible=\"true\"" : "";
  }

  /** An event definition has no shape of its own: it is the icon inside a host event's circle. */
  private String eventWithDefinition(Shape shape) {
    String host = DEFINITION_HOST.getOrDefault(shape.id(), "bpmn:intermediateCatchEvent");
    String definition = shape.tag();
    String extra = definition.equals("bpmn:linkEventDefinition") ? " name=\"Continue\"" : "";

    // Some definitions are not empty elements: the schema requires their payload.
    String body = switch (shape.id()) {
      case "conditional-event-definition" ->
          "<bpmn:condition xsi:type=\"bpmn:tFormalExpression\">${amount &gt; 1000}</bpmn:condition>";
      case "timer-event-definition" ->
          "<bpmn:timeDuration xsi:type=\"bpmn:tFormalExpression\">PT15M</bpmn:timeDuration>";
      default -> null;
    };

    String element = body == null
        ? """
            <%s id="Element_1" name="%s">
              <%s id="Definition_1"%s />
            </%s>""".formatted(host, shape.name(), definition, extra, host)
        : """
            <%s id="Element_1" name="%s">
              <%s id="Definition_1"%s>%s</%s>
            </%s>""".formatted(host, shape.name(), definition, extra, body, definition, host);
    String di = "<bpmndi:BPMNShape id=\"Element_1_di\" bpmnElement=\"Element_1\">%s</bpmndi:BPMNShape>"
        .formatted(bounds(160, 80, 36, 36));
    return document(process(element), di, "Preview_Process");
  }

  // ----------------------------------------------------------------- shapes needing context

  private String boundaryOnTask() {
    String element = """
        <bpmn:task id="Host_1" name="Approve order" />
        <bpmn:boundaryEvent id="Element_1" name="2 days" attachedToRef="Host_1">
          <bpmn:timerEventDefinition id="Definition_1">
            <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">P2D</bpmn:timeDuration>
          </bpmn:timerEventDefinition>
        </bpmn:boundaryEvent>""";
    String di = """
        <bpmndi:BPMNShape id="Host_1_di" bpmnElement="Host_1">%s</bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="Element_1_di" bpmnElement="Element_1">%s</bpmndi:BPMNShape>"""
        .formatted(bounds(160, 80, 100, 80), bounds(212, 142, 36, 36));
    return document(process(element), di, "Preview_Process");
  }

  private String sequenceFlow() {
    String element = """
        <bpmn:task id="From_1" name="Check stock" />
        <bpmn:sequenceFlow id="Element_1" name="in stock" sourceRef="From_1" targetRef="To_1" />
        <bpmn:task id="To_1" name="Ship order" />""";
    String di = """
        <bpmndi:BPMNShape id="From_1_di" bpmnElement="From_1">%s</bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="Element_1_di" bpmnElement="Element_1">
          <di:waypoint x="260" y="120" />
          <di:waypoint x="360" y="120" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNShape id="To_1_di" bpmnElement="To_1">%s</bpmndi:BPMNShape>"""
        .formatted(bounds(160, 80, 100, 80), bounds(360, 80, 100, 80));
    return document(process(element), di, "Preview_Process");
  }

  private String association() {
    String element = """
        <bpmn:task id="Host_1" name="Approve order" />
        <bpmn:textAnnotation id="Note_1"><bpmn:text>SLA: two working days</bpmn:text></bpmn:textAnnotation>
        <bpmn:association id="Element_1" sourceRef="Host_1" targetRef="Note_1" />""";
    String di = """
        <bpmndi:BPMNShape id="Host_1_di" bpmnElement="Host_1">%s</bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="Note_1_di" bpmnElement="Note_1">%s</bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="Element_1_di" bpmnElement="Element_1">
          <di:waypoint x="260" y="100" />
          <di:waypoint x="340" y="70" />
        </bpmndi:BPMNEdge>"""
        .formatted(bounds(160, 80, 100, 80), bounds(340, 40, 140, 60));
    return document(process(element), di, "Preview_Process");
  }

  /** Data associations are declared inside the activity, not at process level. */
  private String dataAssociation(boolean input) {
    // targetRef is required on a data association. An input association points at something inside
    // the activity, so bpmn-js and Camunda Modeler both emit a placeholder property to aim at -
    // matched here, so a saved preview round-trips through those tools unchanged.
    String inside = input
        ? """
            <bpmn:property id="Property_1" name="__targetRef_placeholder" />
              <bpmn:dataInputAssociation id="Element_1">
                <bpmn:sourceRef>Data_1</bpmn:sourceRef>
                <bpmn:targetRef>Property_1</bpmn:targetRef>
              </bpmn:dataInputAssociation>"""
        : """
            <bpmn:dataOutputAssociation id="Element_1">
                <bpmn:targetRef>Data_1</bpmn:targetRef>
              </bpmn:dataOutputAssociation>""";
    String element = """
        <bpmn:dataObjectReference id="Data_1" name="Order" dataObjectRef="DataObject_1" />
        <bpmn:dataObject id="DataObject_1" />
        <bpmn:task id="Host_1" name="Approve order">
          %s
        </bpmn:task>""".formatted(inside);
    String edge = input
        ? """
            <di:waypoint x="218" y="70" />
              <di:waypoint x="218" y="80" />"""
        : """
            <di:waypoint x="218" y="80" />
              <di:waypoint x="218" y="70" />""";
    String di = """
        <bpmndi:BPMNShape id="Data_1_di" bpmnElement="Data_1">%s</bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="Host_1_di" bpmnElement="Host_1">%s</bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="Element_1_di" bpmnElement="Element_1">
          %s
        </bpmndi:BPMNEdge>"""
        .formatted(bounds(200, 20, 36, 50), bounds(160, 80, 100, 80), edge);
    return document(process(element), di, "Preview_Process");
  }

  /** Data inputs and outputs live in an ioSpecification, so the process needs one. */
  private String ioElement(String tag, String name) {
    String set = tag.equals("bpmn:dataInput")
        ? "<bpmn:inputSet id=\"InputSet_1\"><bpmn:dataInputRefs>Element_1</bpmn:dataInputRefs></bpmn:inputSet>"
            + "<bpmn:outputSet id=\"OutputSet_1\" />"
        : "<bpmn:inputSet id=\"InputSet_1\" />"
            + "<bpmn:outputSet id=\"OutputSet_1\"><bpmn:dataOutputRefs>Element_1</bpmn:dataOutputRefs></bpmn:outputSet>";
    String element = """
        <bpmn:ioSpecification id="IoSpec_1">
          <%s id="Element_1" name="%s" />
          %s
        </bpmn:ioSpecification>""".formatted(tag, name, set);
    String di = "<bpmndi:BPMNShape id=\"Element_1_di\" bpmnElement=\"Element_1\">%s</bpmndi:BPMNShape>"
        .formatted(bounds(160, 80, 36, 50));
    return document(process(element), di, "Preview_Process");
  }

  // ----------------------------------------------------------------- collaboration cases

  private String pool() {
    String body = """
        <bpmn:collaboration id="Collaboration_1">
            <bpmn:participant id="Element_1" name="Customer" />
          </bpmn:collaboration>""";
    String di = "<bpmndi:BPMNShape id=\"Element_1_di\" bpmnElement=\"Element_1\" isHorizontal=\"true\">%s</bpmndi:BPMNShape>"
        .formatted(bounds(160, 80, 400, 160));
    return document(body, di, "Collaboration_1");
  }

  private String laneInPool() {
    String body = """
        <bpmn:collaboration id="Collaboration_1">
            <bpmn:participant id="Pool_1" name="Finance" processRef="Preview_Process" />
          </bpmn:collaboration>
          <bpmn:process id="Preview_Process" isExecutable="false">
            <bpmn:laneSet id="LaneSet_1">
              <bpmn:lane id="Element_1" name="Approvers" />
              <bpmn:lane id="Lane_2" name="Clerks" />
            </bpmn:laneSet>
          </bpmn:process>""";
    String di = """
        <bpmndi:BPMNShape id="Pool_1_di" bpmnElement="Pool_1" isHorizontal="true">%s</bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="Element_1_di" bpmnElement="Element_1" isHorizontal="true">%s</bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="Lane_2_di" bpmnElement="Lane_2" isHorizontal="true">%s</bpmndi:BPMNShape>"""
        .formatted(bounds(160, 80, 400, 160), bounds(190, 80, 370, 80), bounds(190, 160, 370, 80));
    return document(body, di, "Collaboration_1");
  }

  private String messageFlow() {
    String body = """
        <bpmn:collaboration id="Collaboration_1">
            <bpmn:participant id="Pool_1" name="Customer" />
            <bpmn:participant id="Pool_2" name="Supplier" />
            <bpmn:messageFlow id="Element_1" name="order" sourceRef="Pool_1" targetRef="Pool_2" />
          </bpmn:collaboration>""";
    String di = """
        <bpmndi:BPMNShape id="Pool_1_di" bpmnElement="Pool_1" isHorizontal="true">%s</bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="Pool_2_di" bpmnElement="Pool_2" isHorizontal="true">%s</bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="Element_1_di" bpmnElement="Element_1">
          <di:waypoint x="360" y="160" />
          <di:waypoint x="360" y="240" />
        </bpmndi:BPMNEdge>"""
        .formatted(bounds(160, 80, 400, 80), bounds(160, 240, 400, 80));
    return document(body, di, "Collaboration_1");
  }

  // ----------------------------------------------------------------- assembly

  private static String process(String elements) {
    return "<bpmn:process id=\"Preview_Process\" isExecutable=\"false\">\n    %s\n  </bpmn:process>"
        .formatted(elements.indent(4).strip());
  }

  private static String bounds(int x, int y, int width, int height) {
    return "<dc:Bounds x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" />".formatted(x, y, width, height);
  }

  private static int orDefault(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private static String document(String body, String di, String planeElement) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                          xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                          xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          id="Preview_Definitions"
                          targetNamespace="http://bpmn.io/schema/bpmn">
          %s
          <bpmndi:BPMNDiagram id="Diagram_1">
            <bpmndi:BPMNPlane id="Plane_1" bpmnElement="%s">
              %s
            </bpmndi:BPMNPlane>
          </bpmndi:BPMNDiagram>
        </bpmn:definitions>
        """.formatted(body, planeElement, di.indent(6).strip());
  }
}
