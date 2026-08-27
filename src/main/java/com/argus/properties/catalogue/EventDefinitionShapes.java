package com.argus.properties.catalogue;

import static com.argus.properties.catalogue.model.Property.BOOLEAN;
import static com.argus.properties.catalogue.model.Property.EXPRESSION;
import static com.argus.properties.catalogue.model.Property.IDREF;
import static com.argus.properties.catalogue.model.Property.STRING;
import static com.argus.properties.catalogue.model.Property.attr;
import static com.argus.properties.catalogue.model.Property.child;
import static com.argus.properties.catalogue.model.Property.ext;
import static com.argus.properties.catalogue.model.Property.requiredAttr;

import com.argus.properties.catalogue.model.Notation;
import com.argus.properties.catalogue.model.Shape;
import java.util.List;

/**
 * The ten event definitions - the child element that gives an event its type and its icon.
 *
 * <p>These are catalogued as shapes because that is how they are experienced: nobody places a
 * "boundary event" and then picks a definition, they place a "timer boundary event". They have no
 * geometry of their own - the circle belongs to the event - so their notation records the icon
 * instead, and their properties are the ones a modeller's panel shows once the type is chosen.
 *
 * <p>Each carries {@code validOn}, in its constraints, because the position/definition matrix is
 * not symmetric and getting it wrong is a deploy-time failure: a terminate event only ends, a
 * cancel event only exists inside a transaction, a link event only pairs throw with catch.
 */
final class EventDefinitionShapes {

  private EventDefinitionShapes() {
  }

  private static Notation icon(String icon) {
    return Notation.none("No geometry of its own - drawn as the " + icon + " inside the host event's circle.");
  }

  static List<Shape> all() {
    return List.of(

        Shape.of("message-event-definition", "Message Event Definition", "bpmn:messageEventDefinition",
                Shape.EVENT_DEFINITION)
            .summary("Point-to-point: one message correlates to one waiting instance. Throwing "
                + "side is implemented like a service task; catching side waits.")
            .notation(icon("envelope, unfilled when catching and filled when throwing"))
            .properties(
                attr("messageRef", IDREF,
                    "The bpmn:message. Correlation matches on that message's name attribute, not "
                        + "its id - two messages with different ids and the same name are the "
                        + "same message to the engine."),
                attr("camunda:class", STRING, "Throwing side only: JavaDelegate to run."),
                attr("camunda:delegateExpression", EXPRESSION, "Throwing side only: delegate bean expression."),
                attr("camunda:expression", EXPRESSION, "Throwing side only: expression to invoke."),
                attr("camunda:resultVariable", STRING, "Throwing side only, with camunda:expression."),
                attr("camunda:type", STRING, "Throwing side only: 'external' for a worker-driven send."),
                attr("camunda:topic", STRING, "Throwing side only, with camunda:type=external."),
                ext("camunda:field", "Injected field on the throwing implementation."))
            .constraints("Valid on: start, intermediate catch, intermediate throw, boundary, end.",
                "Correlation needs the message name plus either a business key or a correlation "
                    + "key - a name alone matches every waiting instance and fails when there is "
                    + "more than one.")
            .build(),

        Shape.of("timer-event-definition", "Timer Event Definition", "bpmn:timerEventDefinition",
                Shape.EVENT_DEFINITION)
            .summary("Fires at a date, after a duration, or on a cycle. Backed by a job, so timer "
                + "accuracy is job executor accuracy, not wall-clock accuracy.")
            .notation(icon("clock"))
            .properties(
                child("bpmn:timeDate",
                    "A fixed point in time, ISO-8601, e.g. 2026-09-01T10:00:00Z, or an expression."),
                child("bpmn:timeDuration",
                    "A delay from when the event is reached, ISO-8601, e.g. PT15M or P2D."),
                child("bpmn:timeCycle",
                    "A repeating interval R5/PT10M, or a cron expression such as 0 0 9 * * ?. "
                        + "Cycles only make sense on a start event or a non-interrupting boundary "
                        + "event - anywhere else the second firing has nothing to trigger."),
                ext("camunda:failedJobRetryTimeCycle", "Retry policy for the timer job itself."))
            .constraints("Valid on: start, intermediate catch, boundary.",
                "Exactly one of timeDate, timeDuration or timeCycle, and it must carry "
                    + "xsi:type=\"bpmn:tFormalExpression\".")
            .build(),

        Shape.of("error-event-definition", "Error Event Definition", "bpmn:errorEventDefinition",
                Shape.EVENT_DEFINITION)
            .summary("A business error - explicitly thrown and explicitly caught. Distinct from a "
                + "technical failure, which retries and then becomes an incident.")
            .notation(icon("lightning bolt, unfilled when catching and filled when throwing"))
            .properties(
                attr("errorRef", IDREF,
                    "The bpmn:error to catch. Omitting it catches every error, which is usually "
                        + "wider than intended."),
                attr("camunda:errorCodeVariable", STRING,
                    "Process variable that receives the error code, so one handler can branch on which error."),
                attr("camunda:errorMessageVariable", STRING,
                    "Process variable that receives the error message."))
            .constraints("Valid on: boundary, end, and start inside an event sub-process.",
                "Matching is on bpmn:error/@errorCode, not on the element id or name.",
                "Always interrupting - an error that did not stop the activity would leave the "
                    + "activity running in a state it already reported as failed.")
            .build(),

        Shape.of("escalation-event-definition", "Escalation Event Definition", "bpmn:escalationEventDefinition",
                Shape.EVENT_DEFINITION)
            .summary("Raises attention without necessarily stopping the work - the non-interrupting "
                + "counterpart to an error.")
            .notation(icon("upward arrowhead"))
            .properties(
                attr("escalationRef", IDREF, "The bpmn:escalation being raised or caught."),
                attr("camunda:escalationCodeVariable", STRING,
                    "Process variable that receives the escalation code."))
            .constraints("Valid on: boundary, intermediate throw, end, and start inside an event sub-process.",
                "Matching is on bpmn:escalation/@escalationCode.")
            .build(),

        Shape.of("signal-event-definition", "Signal Event Definition", "bpmn:signalEventDefinition",
                Shape.EVENT_DEFINITION)
            .summary("Broadcast: every subscription in the engine that matches reacts. Where a "
                + "message is 1:1, a signal is 1:n - including instances you did not have in mind.")
            .notation(icon("triangle"))
            .properties(
                attr("signalRef", IDREF, "The bpmn:signal. Broadcast matches on its name attribute."),
                attr("camunda:async", BOOLEAN, "false",
                    "Throwing side: deliver via a job rather than in the throwing transaction. "
                        + "Worth setting whenever the number of listeners is unbounded."),
                ext("camunda:in", "Passes variables along with the signal to each receiver."))
            .constraints("Valid on: start, intermediate catch, intermediate throw, boundary, end.",
                "A signal is not scoped to a process definition or an instance - throwing one "
                    + "reaches every matching subscription in the engine.")
            .build(),

        Shape.of("conditional-event-definition", "Conditional Event Definition", "bpmn:conditionalEventDefinition",
                Shape.EVENT_DEFINITION)
            .summary("Fires when an expression over process variables becomes true. Evaluated on "
                + "variable change, not polled.")
            .notation(icon("lined page"))
            .properties(
                child("bpmn:condition",
                    "The expression, as xsi:type=\"bpmn:tFormalExpression\", e.g. ${amount > 1000}. "
                        + "A 'language' attribute switches it to a script."),
                attr("camunda:variableName", STRING,
                    "Only re-evaluate when this variable changes. Without it every variable "
                        + "update in scope re-evaluates the condition."),
                attr("camunda:variableEvents", STRING,
                    "Comma-separated subset of create, update, delete to narrow the trigger further."))
            .constraints("Valid on: start, intermediate catch, boundary.",
                "The condition is only evaluated on a variable change inside its scope - setting "
                    + "the variable from outside the scope will not trigger it.")
            .build(),

        Shape.of("link-event-definition", "Link Event Definition", "bpmn:linkEventDefinition",
                Shape.EVENT_DEFINITION)
            .summary("An off-page connector. A throw and a catch sharing a name are two ends of "
                + "one jump, used to avoid drawing a sequence flow across the diagram.")
            .notation(icon("arrow"))
            .properties(
                requiredAttr("name", STRING,
                    "The pairing key. Exactly one catch link must share the name of each throw link."),
                child("bpmn:source", "Reference to the throwing link."),
                child("bpmn:target", "Reference to the catching link."))
            .constraints("Valid on: intermediate catch, intermediate throw.",
                "Both ends must be in the same process level - a link cannot cross into a "
                    + "sub-process or another process.")
            .build(),

        Shape.of("compensate-event-definition", "Compensation Event Definition", "bpmn:compensateEventDefinition",
                Shape.EVENT_DEFINITION)
            .summary("Undoes work that already completed successfully. The only mechanism in BPMN "
                + "that runs activities in reverse order of completion.")
            .notation(icon("rewind, two left-pointing triangles"))
            .properties(
                attr("activityRef", IDREF,
                    "Which completed activity to compensate. Omit to compensate everything in scope."),
                attr("waitForCompletion", BOOLEAN, "true",
                    "Whether the throwing event waits for the handlers to finish before continuing."))
            .constraints("Valid on: intermediate throw, end, boundary, and start inside an event sub-process.",
                "The boundary form attaches to an activity and links to its handler with "
                    + "bpmn:association; the handler carries isForCompensation=\"true\".",
                "Only activities that already completed are compensated - throwing compensation "
                    + "before anything has finished does nothing at all.")
            .build(),

        Shape.of("cancel-event-definition", "Cancel Event Definition", "bpmn:cancelEventDefinition",
                Shape.EVENT_DEFINITION)
            .summary("Aborts a transaction sub-process and triggers compensation of whatever it "
                + "had already completed.")
            .notation(icon("X"))
            .constraints("Valid on: boundary (attached to a bpmn:transaction only), and end "
                    + "(inside a bpmn:transaction only).",
                "Meaningless outside a transaction - the engine rejects it elsewhere.")
            .build(),

        Shape.of("terminate-event-definition", "Terminate Event Definition", "bpmn:terminateEventDefinition",
                Shape.EVENT_DEFINITION)
            .summary("Ends every token in the current scope at once, with no compensation and no "
                + "boundary events fired.")
            .notation(icon("filled circle"))
            .constraints("Valid on: end only.",
                "Scope matters: inside a sub-process it terminates that sub-process and the "
                    + "parent continues; at process level it ends the whole instance.",
                "Nothing is compensated and no boundary event fires - if cleanup is needed, this "
                    + "is the wrong shape.")
            .build());
  }
}
