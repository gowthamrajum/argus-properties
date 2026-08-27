package com.argus.properties.catalogue;

import static com.argus.properties.catalogue.PropertyGroups.BASE_ELEMENT;
import static com.argus.properties.catalogue.PropertyGroups.CAMUNDA_ASYNC;
import static com.argus.properties.catalogue.PropertyGroups.CAMUNDA_EXTENSIONS;
import static com.argus.properties.catalogue.PropertyGroups.CAMUNDA_IO_MAPPING;
import static com.argus.properties.catalogue.PropertyGroups.CAMUNDA_IMPLEMENTATION;
import static com.argus.properties.catalogue.PropertyGroups.EVENT;
import static com.argus.properties.catalogue.PropertyGroups.FLOW_ELEMENT;
import static com.argus.properties.catalogue.PropertyGroups.FLOW_NODE;
import static com.argus.properties.catalogue.model.Property.BOOLEAN;
import static com.argus.properties.catalogue.model.Property.IDREF;
import static com.argus.properties.catalogue.model.Property.INTEGER;
import static com.argus.properties.catalogue.model.Property.STRING;
import static com.argus.properties.catalogue.model.Property.attr;
import static com.argus.properties.catalogue.model.Property.choice;
import static com.argus.properties.catalogue.model.Property.ext;
import static com.argus.properties.catalogue.model.Property.requiredAttr;

import com.argus.properties.catalogue.model.Notation;
import com.argus.properties.catalogue.model.Shape;
import java.util.List;

/**
 * The five event positions.
 *
 * <p>There are far more than five event shapes in a palette - message start, timer boundary,
 * terminate end and so on - but only five tags. A concrete event shape is a position crossed with
 * an event definition, so the definitions are catalogued separately under
 * {@link EventDefinitionShapes} and the two compose. That is also how the XML works, which means a
 * caller can build any of the forty-odd event shapes from these two lists without special cases.
 */
final class EventShapes {

  private EventShapes() {
  }

  private static Notation circle(String render) {
    return Notation.shape(36, 36, false, render);
  }

  static List<Shape> all() {
    return List.of(

        Shape.of("start-event", "Start Event", "bpmn:startEvent", Shape.EVENT)
            .summary("Where an instance begins. A bare one starts on API call; with an event "
                + "definition it starts on a message, timer, signal or condition.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, EVENT)
            .notation(circle("thin single-line circle; dashed when non-interrupting"))
            .properties(
                attr("isInterrupting", BOOLEAN, "true",
                    "Event sub-process start events only. false leaves the enclosing scope "
                        + "running while the handler executes - the difference between handling "
                        + "an escalation and aborting the work that raised it."),
                attr("parallelMultiple", BOOLEAN, "false",
                    "With several event definitions, requires all of them rather than any."),
                attr("camunda:initiator", STRING,
                    "Process variable that receives the id of the user who started the instance. "
                        + "Only populated when the instance is started by an authenticated user."),
                attr("camunda:formKey", STRING, "Start form to render before the instance exists."),
                attr("camunda:formRef", STRING, "Camunda Forms key for the start form."),
                choice("camunda:formRefBinding", "latest", List.of("latest", "deployment", "version"),
                    "Which version of the referenced start form to bind."),
                attr("camunda:formRefVersion", INTEGER, "Required when formRefBinding=version."),
                ext("camunda:formData", "Generated start form fields."))
            .constraints("No incoming sequence flow.",
                "Error, escalation and compensation start events are legal only inside an event sub-process.")
            .example("<bpmn:startEvent id='Start_1' name='Order received' camunda:initiator='starterId'>"
                + "<bpmn:messageEventDefinition messageRef='Msg_Order' /></bpmn:startEvent>")
            .build(),

        Shape.of("intermediate-catch-event", "Intermediate Catch Event", "bpmn:intermediateCatchEvent", Shape.EVENT)
            .summary("Waits in the middle of a flow for something to happen. Always a wait state, "
                + "except for a link event, which is a jump target.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, EVENT)
            .notation(circle("double-line circle with an unfilled icon"))
            .properties(
                attr("parallelMultiple", BOOLEAN, "false",
                    "With several event definitions, requires all of them rather than any."))
            .constraints("Legal definitions: message, timer, conditional, link, signal.",
                "A catch event with no definition is not valid here - use an intermediate throw event.")
            .build(),

        Shape.of("intermediate-throw-event", "Intermediate Throw Event", "bpmn:intermediateThrowEvent", Shape.EVENT)
            .summary("Raises something and continues immediately. With no definition it is a pure "
                + "milestone marker that the engine passes straight through.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, EVENT, CAMUNDA_IMPLEMENTATION)
            .notation(circle("double-line circle with a filled icon"))
            .constraints("Legal definitions: none, message, escalation, link, compensate, signal.",
                "The implementation properties apply only when it carries a message event definition.")
            .build(),

        Shape.of("boundary-event", "Boundary Event", "bpmn:boundaryEvent", Shape.EVENT)
            .summary("Attached to the border of an activity, listening while that activity runs. "
                + "Interrupting kills the activity; non-interrupting spawns a parallel branch and "
                + "leaves it running.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, EVENT)
            .notation(circle("double-line circle on an activity border; solid = interrupting, dashed = non-interrupting"))
            .properties(
                requiredAttr("attachedToRef", IDREF,
                    "The activity this event is attached to. The DI bounds must also overlap that "
                        + "activity's border, or modellers will draw it floating."),
                attr("cancelActivity", BOOLEAN, "true",
                    "true cancels the host activity when the event fires; false leaves it running "
                        + "and starts a concurrent branch."),
                attr("parallelMultiple", BOOLEAN, "false",
                    "With several event definitions, requires all of them rather than any."))
            .constraints("No incoming sequence flow - it is triggered by its host, not by a token.",
                "Error and cancel boundary events are always interrupting.",
                "A compensation boundary event connects to its handler with bpmn:association, not "
                    + "a sequence flow, and has no outgoing flow.",
                "A cancel boundary event may only attach to a bpmn:transaction.")
            .example("<bpmn:boundaryEvent id='Timeout' name='2 days' attachedToRef='Approve' cancelActivity='true'>"
                + "<bpmn:timerEventDefinition><bpmn:timeDuration xsi:type='bpmn:tFormalExpression'>P2D"
                + "</bpmn:timeDuration></bpmn:timerEventDefinition></bpmn:boundaryEvent>")
            .build(),

        Shape.of("end-event", "End Event", "bpmn:endEvent", Shape.EVENT)
            .summary("Consumes the token that reaches it. The instance ends when its last token "
                + "is consumed - so a bare end event ends a branch, not necessarily the instance.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, EVENT, CAMUNDA_IMPLEMENTATION)
            .notation(circle("thick single-line circle with a filled icon"))
            .constraints("No outgoing sequence flow.",
                "camunda:outputParameter is rejected here - an output mapping on an end event "
                    + "would write variables into a scope that is ending. Input parameters are "
                    + "accepted.",
                "Legal definitions: none, message, escalation, error, cancel, compensate, signal, terminate.",
                "A cancel end event is legal only inside a bpmn:transaction.")
            .build());
  }
}
