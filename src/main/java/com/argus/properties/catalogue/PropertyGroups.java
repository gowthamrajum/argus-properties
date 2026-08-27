package com.argus.properties.catalogue;

import static com.argus.properties.catalogue.model.Property.BOOLEAN;
import static com.argus.properties.catalogue.model.Property.EXPRESSION;
import static com.argus.properties.catalogue.model.Property.IDREF;
import static com.argus.properties.catalogue.model.Property.IDREFS;
import static com.argus.properties.catalogue.model.Property.INTEGER;
import static com.argus.properties.catalogue.model.Property.STRING;
import static com.argus.properties.catalogue.model.Property.attr;
import static com.argus.properties.catalogue.model.Property.child;
import static com.argus.properties.catalogue.model.Property.ext;
import static com.argus.properties.catalogue.model.Property.requiredAttr;

import com.argus.properties.catalogue.model.PropertyGroup;
import java.util.List;

/**
 * The property sets shared across whole families of shapes.
 *
 * <p>These exist because BPMN's inheritance is real: {@code bpmn:userTask} is a
 * {@code tActivity} is a {@code tFlowNode} is a {@code tFlowElement} is a {@code tBaseElement},
 * and each level contributes properties. Modelling that as groups rather than copying attributes
 * onto every shape is what keeps {@code id} and {@code camunda:asyncBefore} described identically
 * everywhere, and lets a caller filter the boilerplate out with {@code ?own=true}.
 */
final class PropertyGroups {

  static final String BASE_ELEMENT = "base-element";
  static final String FLOW_ELEMENT = "flow-element";
  static final String FLOW_NODE = "flow-node";
  static final String CAMUNDA_ASYNC = "camunda-async";
  static final String CAMUNDA_EXTENSIONS = "camunda-extensions";
  static final String CAMUNDA_IO_MAPPING = "camunda-io-mapping";
  static final String ACTIVITY = "activity";
  static final String CAMUNDA_IMPLEMENTATION = "camunda-implementation";
  static final String EVENT = "event";

  private PropertyGroups() {
  }

  static List<PropertyGroup> all() {
    return List.of(

        new PropertyGroup(BASE_ELEMENT, "Base element",
            "Carried by every BPMN element without exception.",
            List.of(
                requiredAttr("id", STRING,
                    "Unique within the file. The DI layer points back at it via bpmnElement, so "
                        + "renaming an id without updating the diagram orphans the shape."),
                child("bpmn:documentation",
                    "Free text shown in a modeller's documentation panel; has a textFormat "
                        + "attribute defaulting to text/plain."),
                child("bpmn:extensionElements",
                    "Container for every camunda:* element. Absent unless the shape needs one."))),

        new PropertyGroup(FLOW_ELEMENT, "Flow element",
            "Anything that lives inside a process or sub-process.",
            List.of(
                attr("name", STRING,
                    "The label drawn on or under the shape. Purely presentational - nothing "
                        + "correlates on it except bpmn:message and bpmn:signal names."))),

        new PropertyGroup(FLOW_NODE, "Flow node",
            "Anything a token can sit on: tasks, gateways, events, sub-processes.",
            List.of(
                attr("bpmn:incoming", IDREFS,
                    "One child element per inbound sequence flow. Redundant with the flow's own "
                        + "targetRef, and both must agree or the model will not deploy."),
                attr("bpmn:outgoing", IDREFS,
                    "One child element per outbound sequence flow, mirroring sourceRef."))),

        new PropertyGroup(CAMUNDA_ASYNC, "Camunda async and job configuration",
            "How the engine schedules the node. These are the transaction boundaries of a process "
                + "instance: without one, everything from the last wait state to the next runs in "
                + "a single database transaction and a failure rolls all of it back.",
            List.of(
                attr("camunda:asyncBefore", BOOLEAN, "false",
                    "Commit before this node runs and continue in a background job. The usual way "
                        + "to make a long or failure-prone step retryable on its own."),
                attr("camunda:asyncAfter", BOOLEAN, "false",
                    "Commit after this node completes, before the outgoing flow is taken."),
                attr("camunda:exclusive", BOOLEAN, "true",
                    "Jobs of the same process instance never run concurrently. Turning this off "
                        + "invites optimistic locking failures on shared variables."),
                attr("camunda:jobPriority", EXPRESSION,
                    "Long or expression. Higher priority jobs are acquired first when the job "
                        + "executor is saturated."),
                ext("camunda:failedJobRetryTimeCycle",
                    "ISO-8601 repeating interval controlling retries, e.g. R3/PT10M. Without it "
                        + "the engine uses its default of three near-immediate retries."))),

        new PropertyGroup(CAMUNDA_EXTENSIONS, "Camunda listeners, variable mapping and metadata",
            "The extension elements a flow node carries that are not job configuration. Kept "
                + "apart from the async group because they answer a different question - async "
                + "is about when the engine commits, these are about what runs around the node "
                + "and what data crosses its boundary.",
            List.of(
                ext("camunda:executionListener",
                    "Java or script hook on event=start|end, repeatable. The general-purpose "
                        + "interception point: it fires on every flow node, including gateways "
                        + "and events, where a task listener would not apply."),
                ext("camunda:properties",
                    "Arbitrary key/value metadata. The engine ignores it; tooling may not."))),

        new PropertyGroup(CAMUNDA_IO_MAPPING, "Camunda input/output mapping",
            "Its own group because the elements that accept it are a specific set, not a family. "
                + "BpmnParse.checkActivityInputOutputSupported accepts a tag whose name contains "
                + "'task' or 'Event', plus subProcess, transaction and callActivity - and "
                + "rejects everything else at deploy time with 'camunda:inputOutput mapping "
                + "unsupported for element type'. Gateways and sequence flows are therefore out, "
                + "and so is an event sub-process, which is excluded by name despite being a "
                + "subProcess.",
            List.of(
                ext("camunda:inputOutput",
                    "Local variable mapping in and out of this node's scope, so a delegate does "
                        + "not have to agree with the rest of the process on variable names. "
                        + "camunda:outputParameter carries two further restrictions the engine "
                        + "enforces separately: it is rejected on an end event, and on any "
                        + "multi-instance construct, where each instance would overwrite the "
                        + "last."))),

        new PropertyGroup(ACTIVITY, "Activity",
            "Everything that does work and therefore can loop, be compensated, or carry data "
                + "associations: tasks, sub-processes, call activities.",
            List.of(
                attr("isForCompensation", BOOLEAN, "false",
                    "Marks the activity as a compensation handler. It leaves normal flow entirely "
                        + "- it is reached only by a compensation throw, never by a sequence flow."),
                attr("default", IDREF,
                    "The outgoing sequence flow taken when no condition evaluates true. Declared "
                        + "on the source activity, not on the flow."),
                attr("startQuantity", INTEGER, "1", "Tokens required to start. Rarely anything but 1."),
                attr("completionQuantity", INTEGER, "1", "Tokens produced on completion."),
                child("bpmn:multiInstanceLoopCharacteristics",
                    "Turns the activity into a multi-instance one. Attributes: isSequential, plus "
                        + "camunda:collection and camunda:elementVariable to bind the iteration; "
                        + "children loopCardinality and completionCondition. Note that "
                        + "camunda:outputParameter is rejected on a multi-instance activity: "
                        + "each instance would overwrite the last."),
                child("bpmn:standardLoopCharacteristics",
                    "A while/until loop over the activity. Attributes testBefore and loopMaximum, "
                        + "child loopCondition."),
                child("bpmn:ioSpecification", "Formal data inputs and outputs. Modelling only in Camunda 7."),
                child("bpmn:dataInputAssociation", "Binds a data object reference to a data input."),
                child("bpmn:dataOutputAssociation", "Binds a data output to a data object reference."))),

        new PropertyGroup(CAMUNDA_IMPLEMENTATION, "Camunda implementation",
            "How a service-task-like element is actually carried out. Exactly one of class, "
                + "delegateExpression, expression or type may be set - the engine rejects a "
                + "deployment that sets two, and one that sets none is a no-op.",
            List.of(
                attr("camunda:class", STRING,
                    "Fully-qualified name of a JavaDelegate. Instantiated by the engine, so it "
                        + "gets no Spring injection."),
                attr("camunda:delegateExpression", EXPRESSION,
                    "Expression resolving to a JavaDelegate bean, e.g. ${chargeCardDelegate}. The "
                        + "Spring-friendly choice."),
                attr("camunda:expression", EXPRESSION,
                    "Expression invoked directly, e.g. ${payments.charge(execution)}."),
                attr("camunda:resultVariable", STRING,
                    "The process variable that receives the result. With camunda:expression that "
                        + "is the expression's return value; on a business rule task it is the "
                        + "DMN decision output, shaped by camunda:mapDecisionResult. Meaningless "
                        + "with camunda:class or camunda:delegateExpression, which write their "
                        + "own variables."),
                attr("camunda:type", STRING,
                    "Set to 'external' for the external task pattern; also 'mail', 'shell', "
                        + "'http-connector' where the connect plugin is installed."),
                attr("camunda:topic", STRING,
                    "Required when camunda:type=external. The topic workers subscribe to."),
                attr("camunda:taskPriority", EXPRESSION,
                    "External task priority; workers can fetch highest-priority first."),
                ext("camunda:field",
                    "Injected field on the delegate: name plus stringValue or expression."),
                ext("camunda:connector",
                    "connectorId plus its own inputOutput, for the connect plugin."),
                ext("camunda:errorEventDefinition",
                    "Maps an external task failure onto a BPMN error so a boundary event can "
                        + "catch it, instead of the task simply retrying to zero."))),

        new PropertyGroup(EVENT, "Event",
            "Common to all five event positions.",
            List.of(
                child("eventDefinition",
                    "Zero or one child definition gives the event its type and icon - message, "
                        + "timer, error, escalation, signal, conditional, link, compensate, "
                        + "cancel, terminate. Two or more make it a Multiple event. None makes it "
                        + "a 'none' event."),
                attr("bpmn:eventDefinitionRef", IDREF,
                    "Reference to an event definition declared at bpmn:definitions level, as an "
                        + "alternative to an inline child."),
                child("bpmn:property", "Event-local variable, used to receive a payload."))));
  }
}
