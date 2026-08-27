package com.argus.properties.catalogue;

import static com.argus.properties.catalogue.PropertyGroups.BASE_ELEMENT;
import static com.argus.properties.catalogue.PropertyGroups.FLOW_ELEMENT;
import static com.argus.properties.catalogue.model.Property.BOOLEAN;
import static com.argus.properties.catalogue.model.Property.IDREF;
import static com.argus.properties.catalogue.model.Property.STRING;
import static com.argus.properties.catalogue.model.Property.attr;
import static com.argus.properties.catalogue.model.Property.child;
import static com.argus.properties.catalogue.model.Property.choice;
import static com.argus.properties.catalogue.model.Property.ext;
import static com.argus.properties.catalogue.model.Property.requiredAttr;

import com.argus.properties.catalogue.model.Notation;
import com.argus.properties.catalogue.model.Shape;
import java.util.List;

/**
 * The five connections.
 *
 * <p>Connections are shapes too - they are drawn, they carry properties, and a caller building a
 * diagram needs their geometry rules as much as a task's. They differ in the DI layer: a
 * {@code bpmndi:BPMNEdge} with a list of waypoints rather than a {@code BPMNShape} with bounds.
 */
final class ConnectionShapes {

  private ConnectionShapes() {
  }

  static List<Shape> all() {
    return List.of(

        Shape.of("sequence-flow", "Sequence Flow", "bpmn:sequenceFlow", Shape.CONNECTION)
            .summary("The path a token takes between two flow nodes. Everything about control "
                + "flow in an executable model comes down to these and the gateways they connect.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT)
            .notation(Notation.edge("solid line with a filled arrowhead"))
            .properties(
                requiredAttr("sourceRef", IDREF, "The flow node the token leaves."),
                requiredAttr("targetRef", IDREF, "The flow node the token arrives at."),
                child("bpmn:conditionExpression",
                    "Guard on the flow, e.g. ${approved}. Needs "
                        + "xsi:type=\"bpmn:tFormalExpression\"; a 'language' attribute turns it "
                        + "into a script instead of an expression."),
                attr("isImmediate", BOOLEAN, "BPMN metadata; unused by Camunda."),
                ext("camunda:executionListener",
                    "Hook with event=\"take\", which fires as the flow is taken. The only listener "
                        + "event that belongs on a flow rather than a node."))
            .constraints("Cannot cross a pool boundary - that is what a message flow is for.",
                "The 'default' attribute lives on the source gateway or activity, not on the flow "
                    + "that is default.",
                "A conditional flow out of a parallel gateway is silently ignored.")
            .example("<bpmn:sequenceFlow id='Flow_ok' name='approved' sourceRef='Gw_1' targetRef='Ship'>"
                + "<bpmn:conditionExpression xsi:type='bpmn:tFormalExpression'>${approved}"
                + "</bpmn:conditionExpression></bpmn:sequenceFlow>")
            .build(),

        Shape.of("message-flow", "Message Flow", "bpmn:messageFlow", Shape.CONNECTION)
            .summary("A message crossing between pools. Declarative in Camunda 7: it documents an "
                + "exchange, it does not deliver anything.")
            .notExecutable()
            .inherits(BASE_ELEMENT)
            .notation(Notation.edge("dashed line, open circle at the source, open arrowhead at the target"))
            .properties(
                attr("name", STRING, "Label on the line."),
                requiredAttr("sourceRef", IDREF, "Sending element or pool."),
                requiredAttr("targetRef", IDREF, "Receiving element or pool."),
                attr("messageRef", IDREF, "The bpmn:message being exchanged."))
            .constraints("Declared inside bpmn:collaboration, never inside a process.",
                "Must connect two different participants.")
            .build(),

        Shape.of("association", "Association", "bpmn:association", Shape.CONNECTION)
            .summary("A dotted link with no flow semantics. Two real jobs: attaching a text "
                + "annotation, and connecting a compensation boundary event to its handler.")
            .inherits(BASE_ELEMENT)
            .notation(Notation.edge("dotted line"))
            .properties(
                requiredAttr("sourceRef", IDREF, "Where the association starts."),
                requiredAttr("targetRef", IDREF, "Where it ends."),
                choice("associationDirection", "None", List.of("None", "One", "Both"),
                    "None draws no arrowhead, One draws a single arrowhead, Both draws two."))
            .constraints("Carries no token. Its one execution-relevant use is linking a "
                + "compensation boundary event to the activity that undoes the work.")
            .build(),

        Shape.of("data-input-association", "Data Input Association", "bpmn:dataInputAssociation",
                Shape.CONNECTION)
            .summary("Draws data flowing from a data object into an activity. Modelling only in "
                + "Camunda 7.")
            .notExecutable()
            .inherits(BASE_ELEMENT)
            .notation(Notation.edge("dotted line with an open arrowhead, pointing at the activity"))
            .properties(
                child("bpmn:sourceRef", "The data object or store reference being read."),
                child("bpmn:targetRef", "The bpmn:dataInput receiving it."),
                child("bpmn:transformation", "Expression transforming the value in transit."),
                child("bpmn:assignment", "A from/to pair for finer mapping."))
            .constraints("Declared inside the consuming activity, not at process level.")
            .build(),

        Shape.of("data-output-association", "Data Output Association", "bpmn:dataOutputAssociation",
                Shape.CONNECTION)
            .summary("Draws data flowing out of an activity into a data object. Modelling only in "
                + "Camunda 7.")
            .notExecutable()
            .inherits(BASE_ELEMENT)
            .notation(Notation.edge("dotted line with an open arrowhead, pointing at the data object"))
            .properties(
                child("bpmn:sourceRef", "The bpmn:dataOutput being written."),
                child("bpmn:targetRef", "The data object or store reference receiving it."),
                child("bpmn:transformation", "Expression transforming the value in transit."),
                child("bpmn:assignment", "A from/to pair for finer mapping."))
            .constraints("Declared inside the producing activity, not at process level.")
            .build());
  }

}
