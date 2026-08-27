package com.argus.properties.catalogue;

import static com.argus.properties.catalogue.PropertyGroups.ACTIVITY;
import static com.argus.properties.catalogue.PropertyGroups.BASE_ELEMENT;
import static com.argus.properties.catalogue.PropertyGroups.CAMUNDA_ASYNC;
import static com.argus.properties.catalogue.PropertyGroups.CAMUNDA_EXTENSIONS;
import static com.argus.properties.catalogue.PropertyGroups.CAMUNDA_IO_MAPPING;
import static com.argus.properties.catalogue.PropertyGroups.FLOW_ELEMENT;
import static com.argus.properties.catalogue.PropertyGroups.FLOW_NODE;
import static com.argus.properties.catalogue.model.Property.BOOLEAN;
import static com.argus.properties.catalogue.model.Property.EXPRESSION;
import static com.argus.properties.catalogue.model.Property.IDREF;
import static com.argus.properties.catalogue.model.Property.IDREFS;
import static com.argus.properties.catalogue.model.Property.INTEGER;
import static com.argus.properties.catalogue.model.Property.STRING;
import static com.argus.properties.catalogue.model.Property.attr;
import static com.argus.properties.catalogue.model.Property.child;
import static com.argus.properties.catalogue.model.Property.choice;
import static com.argus.properties.catalogue.model.Property.di;
import static com.argus.properties.catalogue.model.Property.ext;
import static com.argus.properties.catalogue.model.Property.requiredAttr;

import com.argus.properties.catalogue.model.Notation;
import com.argus.properties.catalogue.model.Shape;
import java.util.List;

/**
 * Everything that contains something else: the process itself, pools and lanes, and the four
 * kinds of sub-process.
 *
 * <p>The sub-process family is where the tag/shape distinction bites hardest. Sub Process, Event
 * Sub Process and a collapsed Sub Process are three palette entries, one tag, and the difference
 * between them lives in an attribute ({@code triggeredByEvent}) and a DI flag
 * ({@code isExpanded}) respectively.
 */
final class ContainerShapes {

  private ContainerShapes() {
  }

  private static final List<String> SUBPROCESS_MARKERS = List.of(
      "collapsed: plus in a box, from BPMNShape/@isExpanded=false",
      "multi-instance parallel or sequential: three bars",
      "loop: circular arrow",
      "compensation: rewind glyph, from isForCompensation=true");

  static List<Shape> all() {
    return List.of(

        Shape.of("process", "Process", "bpmn:process", Shape.CONTAINER)
            .summary("The deployable unit. Its id becomes the process definition key, which is "
                + "what every start API call and call activity refers to.")
            .inherits(BASE_ELEMENT)
            .notation(Notation.none("Drawn as the canvas itself, or as the body of the pool that "
                + "references it. Its BPMNPlane carries no bounds."))
            .properties(
                attr("name", STRING, "Human-readable name, shown in Cockpit and Tasklist."),
                attr("isExecutable", BOOLEAN, "false",
                    "Must be true to deploy. The default being false means a process that omits "
                        + "it deploys as documentation and silently never runs."),
                choice("processType", "None", List.of("None", "Public", "Private"), "BPMN metadata; unused by Camunda."),
                attr("isClosed", BOOLEAN, "false", "BPMN metadata; unused by Camunda."),
                attr("camunda:historyTimeToLive", STRING,
                    "How long finished instances survive history cleanup, e.g. P180D. Camunda "
                        + "7.20+ refuses the deployment outright when it is missing and no engine "
                        + "default is configured."),
                attr("camunda:versionTag", STRING,
                    "Human-readable version label, independent of the numeric version. What "
                        + "calledElementBinding=versionTag resolves against."),
                attr("camunda:isStartableInTasklist", BOOLEAN, "true",
                    "Set false for processes only ever started by a call activity or an API call, "
                        + "so they stop cluttering Tasklist."),
                attr("camunda:candidateStarterGroups", STRING, "Comma-separated groups permitted to start it."),
                attr("camunda:candidateStarterUsers", STRING, "Comma-separated users permitted to start it."),
                attr("camunda:jobPriority", EXPRESSION, "Default priority for every job in the process."),
                attr("camunda:taskPriority", EXPRESSION, "Default priority for every external task in the process."),
                child("bpmn:laneSet", "Lanes, if the process is partitioned."),
                ext("camunda:executionListener", "Process-level start and end hooks."),
                ext("camunda:properties", "Arbitrary key/value metadata."))
            .constraints("A file may hold several processes; each one with isExecutable=true "
                + "deploys as its own definition.")
            .example("<bpmn:process id='order-fulfilment' name='Order fulfilment' isExecutable='true' "
                + "camunda:historyTimeToLive='P180D' camunda:versionTag='2.1' />")
            .build(),

        Shape.of("collaboration", "Collaboration", "bpmn:collaboration", Shape.CONTAINER)
            .summary("The wrapper that exists as soon as there is more than one pool. Message "
                + "flows live here, not in any process.")
            .inherits(BASE_ELEMENT)
            .notation(Notation.none("Not drawn. The BPMNPlane references it instead of a process "
                + "when the diagram has pools."))
            .properties(
                attr("name", STRING, "Rarely rendered."),
                attr("isClosed", BOOLEAN, "false", "BPMN metadata; unused by Camunda."),
                child("bpmn:participant", "One per pool."),
                child("bpmn:messageFlow", "Cross-pool messages."))
            .build(),

        Shape.of("participant", "Pool", "bpmn:participant", Shape.CONTAINER)
            .summary("A participant in a collaboration, drawn as a labelled band. With a "
                + "processRef it holds a process; without one it is a black box - a party you "
                + "exchange messages with but do not model.")
            .inherits(BASE_ELEMENT)
            .notation(Notation.shape(600, 250, true, "labelled rectangle; header band on the left when horizontal",
                List.of("multi-participant: three bars, from a participantMultiplicity child")))
            .properties(
                attr("name", STRING, "Drawn vertically in the header band."),
                attr("processRef", IDREF,
                    "The process this pool contains. Omit it for a black-box pool - and note that "
                        + "a black-box pool cannot contain any flow elements at all."),
                child("bpmn:participantMultiplicity", "minimum and maximum, when the pool stands for several parties."),
                di("isHorizontal", BOOLEAN, "Swimlane orientation. Diagram-only."),
                di("isExpanded", BOOLEAN, "false renders the pool as an empty black box."))
            .constraints("Only one pool per file may reference an executable process; the rest "
                + "must be black boxes.")
            .example("<bpmn:participant id='Pool_customer' name='Customer' />")
            .build(),

        Shape.of("lane", "Lane", "bpmn:lane", Shape.CONTAINER)
            .summary("A horizontal band partitioning a pool by role. Purely organisational - the "
                + "engine does not read lanes, so putting a user task in an 'Approvers' lane "
                + "assigns it to nobody.")
            .inherits(BASE_ELEMENT)
            .notation(Notation.shape(570, 125, true, "band inside a pool, with a label header"))
            .properties(
                attr("name", STRING, "The role or department the band represents."),
                attr("bpmn:flowNodeRef", IDREFS,
                    "One child element per flow node in this lane. Membership is stored here, not "
                        + "by geometry - a shape can sit visually in one lane and belong to another."),
                attr("partitionElementRef", IDREF, "The resource this lane stands for."),
                child("bpmn:childLaneSet", "Nested lanes."),
                di("isHorizontal", BOOLEAN, "Orientation, inherited in practice from the pool."))
            .constraints("No execution semantics in Camunda 7. Use camunda:candidateGroups on the "
                + "user task for real assignment.")
            .build(),

        Shape.of("lane-set", "Lane Set", "bpmn:laneSet", Shape.CONTAINER)
            .summary("The container holding a process's lanes. Never drawn itself.")
            .inherits(BASE_ELEMENT)
            .notation(Notation.none("Not drawn; only its lanes are."))
            .properties(
                attr("name", STRING, "Rarely used."),
                child("bpmn:lane", "One or more lanes."))
            .build(),

        Shape.of("sub-process", "Sub Process", "bpmn:subProcess", Shape.CONTAINER)
            .summary("An embedded scope. Worth using for what a scope gives you - a boundary "
                + "event over several activities, a multi-instance block, a compensation "
                + "boundary - rather than for tidiness alone.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, ACTIVITY)
            .notation(Notation.shape(350, 200, true,
                "rounded rectangle containing its own flow; 100x80 when collapsed", SUBPROCESS_MARKERS))
            .properties(
                attr("triggeredByEvent", BOOLEAN, "false",
                    "true turns this same tag into an Event Sub Process - see the "
                        + "event-sub-process entry."),
                di("isExpanded", BOOLEAN,
                    "Whether the contents are drawn. This is a diagram flag, not a behaviour one: "
                        + "the engine reads it off the BPMNShape and records it as an activity "
                        + "property for Cockpit to render with, and nothing branches on it. A "
                        + "collapsed sub-process contains and executes everything inside it, "
                        + "exactly as an expanded one does."))
            .constraints("Must contain exactly one none start event.",
                "Shares the parent's variable scope for reads, but variables it creates are local "
                    + "unless explicitly written to the parent.",
                "An empty one does not deploy: BpmnParse rejects it with 'subProcess must define a "
                    + "startEvent element'. This is why a collapsed sub-process is often believed "
                    + "not to be executable - a freshly drawn one is empty, and emptiness is the "
                    + "problem, not collapse.",
                "Collapsing is not symmetric in tooling. Core bpmn-js lets you create a collapsed "
                    + "sub-process and, since 9.0, drill into it to fill it - but offers no way to "
                    + "collapse one that is already expanded. bpmn-io shipped "
                    + "bpmn-js-collapse-subprocess to re-enable that, and archived it in February "
                    + "2025. Editing isExpanded in the XML works regardless: it is one attribute, "
                    + "and the engine does not care either way.")
            .example("<bpmn:subProcess id='Sub_payment' name='Handle payment' camunda:asyncBefore='true' />")
            .build(),

        Shape.of("event-sub-process", "Event Sub Process", "bpmn:subProcess", Shape.CONTAINER)
            .summary("A handler that sits inside a scope and waits for an event, instead of being "
                + "reached by a sequence flow. The in-scope alternative to a boundary event.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, ACTIVITY)
            .notation(Notation.shape(350, 200, true, "dotted rounded rectangle"))
            .properties(
                requiredAttr("triggeredByEvent", BOOLEAN,
                    "Must be true. This attribute is the entire difference between this shape and "
                        + "an ordinary sub-process."))
            .constraints("No incoming or outgoing sequence flows.",
                "Exactly one start event, and it must carry an event definition.",
                "camunda:inputOutput is rejected at deploy time. The parser excludes subProcess "
                    + "with triggeredByEvent=true by name, so an event sub-process is the one "
                    + "sub-process that cannot carry a variable mapping.",
                "Its start event's isInterrupting decides whether the enclosing scope is cancelled "
                    + "or keeps running alongside the handler.")
            .example("<bpmn:subProcess id='Sub_onError' name='On error' triggeredByEvent='true' />")
            .build(),

        Shape.of("transaction", "Transaction Sub Process", "bpmn:transaction", Shape.CONTAINER)
            .summary("A sub-process with all-or-nothing semantics: cancelling it compensates "
                + "everything inside that had already completed.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, ACTIVITY)
            .notation(Notation.shape(350, 200, true, "double-line rounded rectangle", SUBPROCESS_MARKERS))
            .properties(
                choice("method", "##Compensate", List.of("##Compensate", "##Store", "##Image"),
                    "How the transaction is undone. Camunda implements the compensation variant."))
            .constraints("This is BPMN compensation, not a database transaction - it does not roll "
                + "anything back by itself, it runs the compensation handlers you modelled.",
                "Pairs with a cancel end event inside and a cancel boundary event outside.")
            .build(),

        Shape.of("ad-hoc-sub-process", "Ad-hoc Sub Process", "bpmn:adHocSubProcess", Shape.CONTAINER)
            .summary("A bag of activities performed in no fixed order, at the performer's "
                + "discretion. Modelling only - the Camunda 7 engine does not execute it.")
            .notExecutable()
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, ACTIVITY)
            .notation(Notation.shape(350, 200, true, "rounded rectangle with a tilde at the bottom centre",
                List.of("ad-hoc: tilde (~)")))
            .properties(
                choice("ordering", "Parallel", List.of("Parallel", "Sequential"),
                    "Whether the contained activities may run concurrently."),
                attr("cancelRemainingInstances", BOOLEAN, "true",
                    "Whether unfinished activities are cancelled once the completion condition holds."),
                child("bpmn:completionCondition", "Expression that ends the ad-hoc scope."))
            .constraints("Not supported by the Camunda 7 / Fluxnova engine.")
            .build());
  }
}
