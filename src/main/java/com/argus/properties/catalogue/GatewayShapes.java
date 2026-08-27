package com.argus.properties.catalogue;

import static com.argus.properties.catalogue.PropertyGroups.BASE_ELEMENT;
import static com.argus.properties.catalogue.PropertyGroups.CAMUNDA_ASYNC;
import static com.argus.properties.catalogue.PropertyGroups.CAMUNDA_EXTENSIONS;
import static com.argus.properties.catalogue.PropertyGroups.FLOW_ELEMENT;
import static com.argus.properties.catalogue.PropertyGroups.FLOW_NODE;
import static com.argus.properties.catalogue.model.Property.BOOLEAN;
import static com.argus.properties.catalogue.model.Property.IDREF;
import static com.argus.properties.catalogue.model.Property.attr;
import static com.argus.properties.catalogue.model.Property.child;
import static com.argus.properties.catalogue.model.Property.choice;
import static com.argus.properties.catalogue.model.Property.di;

import com.argus.properties.catalogue.model.Notation;
import com.argus.properties.catalogue.model.Property;
import com.argus.properties.catalogue.model.Shape;
import java.util.List;

/**
 * The five gateways.
 *
 * <p>Every one is a 50x50 diamond; the marker inside it is the whole difference. Note that the
 * exclusive gateway's X is the only marker in BPMN controlled by the diagram rather than the
 * model - {@code isMarkerVisible} lives on the BPMNShape, so two files with identical execution
 * semantics can render differently.
 */
final class GatewayShapes {

  private GatewayShapes() {
  }

  private static Property gatewayDirection() {
    return choice("gatewayDirection", "Unspecified",
        List.of("Unspecified", "Converging", "Diverging", "Mixed"),
        "Declares whether this gateway splits, joins, or both. Camunda infers the behaviour from "
            + "the actual flow count and ignores a contradicting value.");
  }

  private static Property defaultFlow() {
    return attr("default", IDREF,
        "The outgoing sequence flow taken when no condition is true. Rendered with a backslash "
            + "tick. Without one, a token that matches nothing throws instead of continuing.");
  }

  static List<Shape> all() {
    return List.of(

        Shape.of("exclusive-gateway", "Exclusive Gateway", "bpmn:exclusiveGateway", Shape.GATEWAY)
            .summary("Takes exactly one outgoing flow: the first whose condition evaluates true, "
                + "in document order, else the default.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS)
            .notation(Notation.shape(50, 50, false, "diamond",
                List.of("X, drawn only when the BPMNShape sets isMarkerVisible=true")))
            .properties(
                gatewayDirection(),
                defaultFlow(),
                di("isMarkerVisible", BOOLEAN,
                    "Whether the X is drawn. A diagram-only property: it changes nothing about "
                        + "how the gateway executes."))
            .constraints("Evaluation order is document order, so reordering sequence flows in the "
                + "XML can change which branch wins when two conditions overlap.")
            .example("<bpmn:exclusiveGateway id='Gw_1' name='Approved?' default='Flow_reject' />")
            .build(),

        Shape.of("parallel-gateway", "Parallel Gateway", "bpmn:parallelGateway", Shape.GATEWAY)
            .summary("Forks into every outgoing flow, and joins by waiting for every incoming one. "
                + "Conditions on its outgoing flows are ignored entirely.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS)
            .notation(Notation.shape(50, 50, false, "diamond with a plus", List.of("plus, always visible")))
            .properties(gatewayDirection())
            .constraints("No 'default' attribute and no conditional flows - a condition here is "
                + "silently ignored, which is a common source of branches that always run.",
                "A join blocks forever if an upstream branch cannot produce a token, which is the "
                    + "classic deadlock: an exclusive split feeding a parallel join.")
            .build(),

        Shape.of("inclusive-gateway", "Inclusive Gateway", "bpmn:inclusiveGateway", Shape.GATEWAY)
            .summary("Takes every outgoing flow whose condition is true - one, several, or all. "
                + "Joining waits for exactly the branches that were actually taken.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS)
            .notation(Notation.shape(50, 50, false, "diamond with a circle", List.of("circle, always visible")))
            .properties(gatewayDirection(), defaultFlow())
            .constraints("The join must reason about which tokens can still arrive, so it is "
                + "markedly more expensive than a parallel join on wide models.")
            .build(),

        Shape.of("event-based-gateway", "Event-based Gateway", "bpmn:eventBasedGateway", Shape.GATEWAY)
            .summary("A race. Each outgoing flow leads to a catching event; the first event to "
                + "occur wins and the others are cancelled. The idiomatic way to model a timeout "
                + "on waiting for a message.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS)
            .notation(Notation.shape(50, 50, false, "diamond containing a double circle with a pentagon"))
            .properties(
                gatewayDirection(),
                attr("instantiate", BOOLEAN, "false",
                    "true lets the gateway start a process instance rather than route inside one."),
                choice("eventGatewayType", "Exclusive", List.of("Exclusive", "Parallel"),
                    "Exclusive is the race. Parallel waits for all of them and is not executable "
                        + "in Camunda 7."))
            .constraints("Every outgoing flow must target an intermediate catch event (message, "
                + "timer, signal, conditional) or a receive task.",
                "Outgoing flows must not carry conditions - the events decide, not the flows.")
            .example("<bpmn:eventBasedGateway id='Gw_wait' name='Reply or timeout' />")
            .build(),

        Shape.of("complex-gateway", "Complex Gateway", "bpmn:complexGateway", Shape.GATEWAY)
            .summary("Arbitrary merge/split semantics expressed as an activation condition. Drawn "
                + "by every modeller and executed by none - the Camunda 7 engine rejects it.")
            .notExecutable()
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE)
            .notation(Notation.shape(50, 50, false, "diamond with an asterisk"))
            .properties(
                gatewayDirection(),
                defaultFlow(),
                child("bpmn:activationCondition", "Expression deciding when the gateway fires."))
            .constraints("Not supported by the Camunda 7 / Fluxnova engine. Model the intent with "
                + "inclusive or event-based gateways instead.")
            .build());
  }
}
