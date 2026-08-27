package com.argus.properties.catalogue;

import com.argus.properties.catalogue.model.ListenerEvent;
import com.argus.properties.catalogue.model.ListenerType;
import java.util.List;

/**
 * The two listener families and the events each one offers.
 *
 * <p>The property catalogue can say a user task accepts {@code camunda:taskListener} and still
 * leave the only decision that matters unanswered: which event. Six values behave differently
 * enough that picking the wrong one produces a listener that either never fires or fires at a
 * moment when the thing it wants to read does not exist yet.
 */
final class Listeners {

  private static final List<String> FLOW_NODES_AND_PROCESS = List.of(
      "process", "task", "user-task", "service-task", "send-task", "receive-task", "manual-task",
      "script-task", "business-rule-task", "sub-process", "event-sub-process", "transaction",
      "call-activity", "exclusive-gateway", "parallel-gateway", "inclusive-gateway",
      "event-based-gateway", "start-event", "intermediate-catch-event", "intermediate-throw-event",
      "boundary-event", "end-event");

  private Listeners() {
  }

  static List<ListenerType> all() {
    return List.of(

        new ListenerType("execution", "Execution listeners", "camunda:executionListener",
            "Every flow node, the process itself, and sequence flows.",
            "Run code as a token moves, without adding a shape to the diagram.",
            List.of(
                new ListenerEvent("start", "Start",
                    "The token arrives at the element, before the element's own behaviour runs. On "
                        + "the process element it fires once, as the instance starts.",
                    "Setting up variables the activity is about to need, or recording that work began.",
                    "It runs before the activity does anything - a start listener on a service task "
                        + "cannot read that task's result, because there is not one yet.",
                    FLOW_NODES_AND_PROCESS),
                new ListenerEvent("end", "End",
                    "The element has finished its behaviour, before any outgoing sequence flow is "
                        + "evaluated. On the process element it fires as the instance ends.",
                    "Reading what the activity produced, cleaning up, emitting an audit record.",
                    "It runs before the outgoing flow conditions are evaluated, so a variable set "
                        + "here does affect which branch is taken.",
                    FLOW_NODES_AND_PROCESS),
                new ListenerEvent("take", "Take",
                    "A sequence flow is taken, after the source element's end listener and before "
                        + "the target's start listener.",
                    "Logging which branch a token actually went down.",
                    "Only valid on a sequence flow. Putting event=\"take\" on an activity is not a "
                        + "listener that never fires - it is a deployment failure.",
                    List.of("sequence-flow"))),
            List.of(
                "camunda:class - a JavaDelegate or ExecutionListener implementation, instantiated by the engine",
                "camunda:delegateExpression - an expression resolving to a listener bean, e.g. ${auditListener}",
                "camunda:expression - an expression invoked directly, e.g. ${audit.record(execution)}",
                "camunda:script - an inline script, or camunda:resource pointing at a deployed one"),
            List.of(
                "Listeners on the same element and event run in the order they appear in the XML.",
                "A listener throwing a technical exception fails the transaction it runs in. Where "
                    + "there is no async boundary that is the caller's transaction, so the failure "
                    + "surfaces somewhere other than the activity that caused it.",
                "A listener is invisible on the diagram. Behaviour that a reader of the model needs "
                    + "to know about is better as a shape.")),

        new ListenerType("task", "Task listeners", "camunda:taskListener",
            "User tasks only. No other shape accepts one.",
            "Run code as a human task changes state - created, assigned, completed.",
            List.of(
                new ListenerEvent("create", "Create",
                    "The task has been created and all of its properties are set.",
                    "Notifying someone that work is waiting, or deriving a due date.",
                    "Fires once per task. A task re-appearing after a failed completion is not "
                        + "created again.",
                    List.of("user-task")),
                new ListenerEvent("assignment", "Assignment",
                    "The task's assignee is set - whether at creation, or later by a claim.",
                    "Notifying the specific person, rather than a whole candidate group.",
                    "The documented order is that assignment fires after create when the assignee "
                        + "is already set at creation. Community reports have described the "
                        + "opposite order, so code that depends on which of the two runs first is "
                        + "worth avoiding.",
                    List.of("user-task")),
                new ListenerEvent("update", "Update",
                    "A property of an already-created task changes - assignee, owner, priority, "
                        + "due date.",
                    "Auditing edits to a task after it exists.",
                    "Creation is not an update: this does not fire when the task is first made. "
                        + "Available from Camunda 7.9.",
                    List.of("user-task")),
                new ListenerEvent("complete", "Complete",
                    "The task has been completed successfully, immediately before it is deleted.",
                    "Reading the variables the user submitted, or validating them.",
                    "Throwing a BpmnError here still stops the completion, which makes it a place "
                        + "to reject a submission - at the cost of the user seeing a failure from "
                        + "somewhere they did not expect.",
                    List.of("user-task")),
                new ListenerEvent("delete", "Delete",
                    "The task is about to be removed without being completed.",
                    "Cancelling whatever the create listener set up elsewhere.",
                    "Fires for every way a task can disappear unfinished: an interrupting boundary "
                        + "event, a cancelled instance, a terminate end event.",
                    List.of("user-task")),
                new ListenerEvent("timeout", "Timeout",
                    "A timer defined on the listener itself expires.",
                    "Escalating or reminding without drawing a boundary event.",
                    "Needs an id on the listener and a bpmn:timerEventDefinition child, and needs "
                        + "the job executor running. Available from Camunda 7.12.",
                    List.of("user-task"))),
            List.of(
                "camunda:class - a TaskListener implementation",
                "camunda:delegateExpression - an expression resolving to a TaskListener bean",
                "camunda:expression - an expression invoked directly",
                "camunda:script - an inline script, or camunda:resource pointing at a deployed one"),
            List.of(
                "Only bpmn:userTask accepts a task listener. Everything else - including the other "
                    + "seven task types - takes an execution listener instead.",
                "The listener receives a DelegateTask, not a DelegateExecution: it can read and set "
                    + "task properties directly.")));
  }
}
