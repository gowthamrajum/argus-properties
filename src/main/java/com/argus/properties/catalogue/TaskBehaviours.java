package com.argus.properties.catalogue;

import static com.argus.properties.catalogue.model.Behaviour.IMPLEMENTATION_DEPENDENT;
import static com.argus.properties.catalogue.model.Behaviour.PASS_THROUGH;
import static com.argus.properties.catalogue.model.Behaviour.SAVE_POINT_ALWAYS;
import static com.argus.properties.catalogue.model.Behaviour.SAVE_POINT_IMPLEMENTATION_DEPENDENT;
import static com.argus.properties.catalogue.model.Behaviour.SAVE_POINT_ON_ASYNC;
import static com.argus.properties.catalogue.model.Behaviour.SYNCHRONOUS;
import static com.argus.properties.catalogue.model.Behaviour.WAIT_STATE;
import static com.argus.properties.catalogue.model.Outcome.BPMN_ERROR;
import static com.argus.properties.catalogue.model.Outcome.COMPLETED;
import static com.argus.properties.catalogue.model.Outcome.INCIDENT;
import static com.argus.properties.catalogue.model.Outcome.ROLLBACK;
import static com.argus.properties.catalogue.model.Outcome.STUCK;
import static com.argus.properties.catalogue.model.Outcome.WAITING;
import static com.argus.properties.catalogue.model.Outcome.of;

import com.argus.properties.catalogue.model.Behaviour;
import com.argus.properties.catalogue.model.RetryProfile;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What each task shape does at run time.
 *
 * <p>Tasks are the interesting half of the catalogue behaviourally, because they are where work
 * actually happens and therefore where it fails. The recurring theme across all eight is that the
 * damaging outcome is rarely the loud one: an incident is at least visible in Cockpit, whereas a
 * user task nobody can see and an external task nobody serves both look exactly like a healthy
 * instance that has not finished yet.
 */
final class TaskBehaviours {

  private TaskBehaviours() {
  }

  /** A technical failure with no save point in front of it unwinds the caller's transaction. */
  private static com.argus.properties.catalogue.model.Outcome rollbackWhenSynchronous(String what) {
    return of(ROLLBACK,
        "%s throws a technical exception and the task has no camunda:asyncBefore".formatted(what),
        "The exception propagates to whatever started the transaction - an API call, a job, or the "
            + "previous save point - and everything since that point is undone. No incident is "
            + "created and no job is left behind, so nothing records that the work was attempted.",
        "None automatically. The caller decides whether to retry, and re-runs the whole segment.");
  }

  private static com.argus.properties.catalogue.model.Outcome incidentWhenAsync(String what) {
    return of(INCIDENT,
        "%s throws a technical exception and a job exists, because the task is asynchronous".formatted(what),
        "The job's retry count is decremented and it is rescheduled. When retries reach zero an "
            + "incident is raised and the instance stops at this activity.",
        "An operator increments the retries in Cockpit, or the job is re-executed after the "
            + "underlying fault is fixed.");
  }

  private static com.argus.properties.catalogue.model.Outcome bpmnError(String what) {
    return of(BPMN_ERROR,
        "%s throws a BpmnError".formatted(what),
        "Treated as a modelled failure, not a fault: no retry, no incident. The engine looks for a "
            + "matching error boundary event on this activity, then an error event sub-process, "
            + "then propagates upward.",
        "Caught by an error boundary event. If nothing catches it, the instance fails with an "
            + "unhandled BPMN error.");
  }

  static Map<String, Behaviour> all() {
    Map<String, Behaviour> behaviours = new LinkedHashMap<>();

    behaviours.put("task", Behaviour.of(PASS_THROUGH, SAVE_POINT_ON_ASYNC)
        .outcomes(
            of(COMPLETED, "The token arrives",
                "The engine does no work and the token leaves immediately along the outgoing flow."),
            bpmnError("An execution listener"),
            incidentWhenAsync("An execution listener"),
            rollbackWhenSynchronous("An execution listener"))
        .retries(RetryProfile.standard())
        .notes("An abstract task is a placeholder. It has no engine behaviour of its own, so the "
                + "only way it can fail is through a listener attached to it.",
            "Deploys and runs fine, which makes it easy to leave in a model as an unimplemented "
                + "step that silently does nothing.")
        .build());

    behaviours.put("user-task", Behaviour.of(WAIT_STATE, SAVE_POINT_ALWAYS)
        .outcomes(
            of(WAITING, "The token arrives and the task is created",
                "The engine commits and the instance parks. The task appears in Tasklist for "
                    + "whoever the assignee or candidate expressions resolved to.",
                "Someone completes the task through Tasklist or the API."),
            of(COMPLETED, "The task is completed",
                "Variables submitted with the completion are set, and the token leaves."),
            of(STUCK, "The assignment resolves to nobody - an empty assignee, or a candidate group "
                    + "with no members",
                "The task is created successfully and waits, but appears in nobody's list. The "
                    + "instance looks healthy and simply never progresses.",
                "An operator assigns the task manually, once someone notices."),
            bpmnError("A task listener"),
            incidentWhenAsync("An assignment expression or a task listener"),
            rollbackWhenSynchronous("An assignment expression or a create listener"))
        .retries(RetryProfile.standard())
        .notes("camunda:dueDate is informational only. Nothing happens when it passes - a timer "
                + "boundary event is the only thing that acts on a deadline.",
            "Assignment expressions are evaluated when the task is created, so a missing variable "
                + "fails on entry rather than at completion.")
        .build());

    behaviours.put("service-task", Behaviour.of(IMPLEMENTATION_DEPENDENT, SAVE_POINT_IMPLEMENTATION_DEPENDENT)
        .outcomes(
            of(COMPLETED, "The delegate returns, or an external worker reports completion",
                "Any camunda:resultVariable is set and the token leaves."),
            of(WAITING, "camunda:type is external",
                "The engine creates an external task and commits. The instance parks until a "
                    + "worker fetches and locks it.",
                "A worker completes the external task."),
            of(STUCK, "camunda:type is external and no worker ever subscribes to the topic",
                "The external task sits unfetched indefinitely. No incident, no error, no log "
                    + "entry - the instance is simply never picked up.",
                "Start a worker on the topic. Nothing in the engine will surface this on its own, "
                    + "so a timer boundary event is the only way to make it visible."),
            bpmnError("The delegate, or a worker calling handleBpmnError"),
            incidentWhenAsync("The delegate, or a worker reporting a failure"),
            rollbackWhenSynchronous("The delegate"))
        .retries(RetryProfile.standard())
        .notes("The execution kind depends entirely on the implementation. A Java class, delegate "
                + "expression, expression or connector runs synchronously inside the current "
                + "transaction; camunda:type=external turns the same shape into a wait state.",
            "An external worker controls its own retries when it reports a failure, so the engine "
                + "default of three does not apply to that path.",
            "A synchronous delegate that calls a remote service holds the transaction - and its "
                + "database locks - for the duration of that call.")
        .build());

    behaviours.put("send-task", Behaviour.of(IMPLEMENTATION_DEPENDENT, SAVE_POINT_IMPLEMENTATION_DEPENDENT)
        .outcomes(
            of(COMPLETED, "The delegate returns, or an external worker reports completion",
                "The message is considered sent and the token leaves."),
            of(WAITING, "camunda:type is external",
                "Behaves exactly like an external service task: the engine commits and waits for a "
                    + "worker.",
                "A worker completes the external task."),
            of(STUCK, "camunda:type is external and no worker serves the topic",
                "The send never happens and nothing reports that it did not. Downstream steps that "
                    + "assume the message went out are simply never reached.",
                "Start a worker on the topic. As with any external task, a timer boundary event is "
                    + "the only thing that will surface the wait on its own."),
            bpmnError("The delegate, or a worker calling handleBpmnError"),
            incidentWhenAsync("The delegate"),
            rollbackWhenSynchronous("The delegate"))
        .retries(RetryProfile.standard())
        .notes("To the engine this is a service task with different notation. It carries no "
                + "messaging behaviour of its own: the send is whatever the implementation does.")
        .build());

    behaviours.put("receive-task", Behaviour.of(WAIT_STATE, SAVE_POINT_ALWAYS)
        .outcomes(
            of(WAITING, "The token arrives",
                "The engine commits and registers a message subscription for the referenced "
                    + "message name. The instance parks.",
                "The message is correlated, by name and by whatever correlation keys the call "
                    + "supplies."),
            of(COMPLETED, "A matching message is correlated",
                "Any variables passed with the correlation are set and the token leaves."),
            of(STUCK, "The message never arrives",
                "The subscription stays open for the life of the instance. Nothing times it out.",
                "Correlate the message, or attach a timer boundary event so the wait has a bound."))
        .retries(RetryProfile.none("A receive task performs no work, so there is nothing to fail "
            + "and nothing to retry. It either receives its message or it does not."))
        .notes("Correlation matches on message name plus any business key or correlation keys the "
                + "caller supplies. A name that matches several waiting instances raises a "
                + "correlation exception in the caller, not in the process.",
            "An event-based gateway followed by a receive task is the idiomatic way to put a "
                + "timeout on this wait.")
        .build());

    behaviours.put("manual-task", Behaviour.of(PASS_THROUGH, SAVE_POINT_ON_ASYNC)
        .outcomes(
            of(COMPLETED, "The token arrives",
                "The engine does nothing and the token leaves immediately."),
            bpmnError("An execution listener"),
            incidentWhenAsync("An execution listener"),
            rollbackWhenSynchronous("An execution listener"))
        .retries(RetryProfile.standard())
        .notes("Documents work happening outside the system. Despite the name it is not a wait "
                + "state - the engine does not stop here, which routinely surprises people who "
                + "expect it to behave like a user task.")
        .build());

    behaviours.put("script-task", Behaviour.of(SYNCHRONOUS, SAVE_POINT_ON_ASYNC)
        .outcomes(
            of(COMPLETED, "The script runs to the end",
                "If camunda:resultVariable is set it takes the script's return value - which may "
                    + "be null, with nothing to warn you. The token leaves."),
            bpmnError("The script throws a BpmnError"),
            incidentWhenAsync("The script throws any other exception, including a compilation error"),
            rollbackWhenSynchronous("The script"))
        .retries(RetryProfile.standard())
        .notes("The script runs on the engine thread inside the current transaction, so a blocking "
                + "call in it holds that transaction open.",
            "A missing scriptFormat is a deployment failure, not a run-time one.",
            "Reading an absent variable throws, so an unguarded execution.getVariable fails the "
                + "activity rather than yielding null.")
        .build());

    behaviours.put("business-rule-task", Behaviour.of(IMPLEMENTATION_DEPENDENT, SAVE_POINT_IMPLEMENTATION_DEPENDENT)
        .outcomes(
            of(COMPLETED, "The decision evaluates, or the delegate returns",
                "The result is written to camunda:resultVariable, shaped by "
                    + "camunda:mapDecisionResult. The token leaves."),
            of(WAITING, "camunda:type is external",
                "Behaves as an external task rather than evaluating a decision locally.",
                "A worker completes the external task."),
            of(INCIDENT, "The referenced decision is not deployed, or evaluation throws",
                "A missing decisionRef fails at run time, not at deploy time - the reference is "
                    + "resolved when the token arrives.",
                "Deploy the decision, then retry the job."),
            bpmnError("The decision logic or a delegate"),
            rollbackWhenSynchronous("Decision evaluation"))
        .retries(RetryProfile.standard())
        .notes("A DMN decision is evaluated synchronously in the current transaction.",
            "camunda:decisionRef is resolved at run time, so a typo in it deploys cleanly and "
                + "fails only when an instance reaches the task.",
            "With camunda:mapDecisionResult unset, a multi-row result lands as a list of maps, "
                + "which downstream expressions rarely expect.")
        .build());

    return behaviours;
  }
}
