package com.argus.properties.catalogue;

import static com.argus.properties.catalogue.model.Behaviour.PASS_THROUGH;
import static com.argus.properties.catalogue.model.Behaviour.SAVE_POINT_ALWAYS;
import static com.argus.properties.catalogue.model.Behaviour.SAVE_POINT_ON_ASYNC;
import static com.argus.properties.catalogue.model.Behaviour.SYNCHRONOUS;
import static com.argus.properties.catalogue.model.Behaviour.WAIT_STATE;
import static com.argus.properties.catalogue.model.Outcome.COMPLETED;
import static com.argus.properties.catalogue.model.Outcome.INCIDENT;
import static com.argus.properties.catalogue.model.Outcome.STUCK;
import static com.argus.properties.catalogue.model.Outcome.WAITING;
import static com.argus.properties.catalogue.model.Outcome.of;

import com.argus.properties.catalogue.model.Behaviour;
import com.argus.properties.catalogue.model.EventShape;
import com.argus.properties.catalogue.model.Outcome;
import com.argus.properties.catalogue.model.RetryProfile;
import java.util.ArrayList;
import java.util.List;

/**
 * Behaviour for the derived event shapes, composed from three tiers rather than written out
 * forty-nine times.
 *
 * <ul>
 *   <li><b>Position</b> decides the shape of execution - a catch waits, a throw runs straight
 *       through, a boundary is armed by its host rather than reached by a token.</li>
 *   <li><b>Definition</b> decides what fires it, and what can go wrong while waiting for that.</li>
 *   <li><b>The interrupting flag</b> decides what happens to the host when it does fire.</li>
 * </ul>
 *
 * <p>Composing means a correction to "how a timer fires" lands on every timer shape at once, which
 * is the whole reason not to hand-write them.
 */
final class EventBehaviours {

  private EventBehaviours() {
  }

  static Behaviour compose(EventShape shape) {
    String position = shape.positionShapeId();
    String trigger = shape.definitionShapeId() == null
        ? "none"
        : shape.definitionShapeId().replace("-event-definition", "");

    List<Outcome> outcomes = new ArrayList<>();
    List<String> notes = new ArrayList<>();

    String executionKind = executionKindOf(position, trigger);
    String savePoint = WAIT_STATE.equals(executionKind) ? SAVE_POINT_ALWAYS : SAVE_POINT_ON_ASYNC;

    positionOutcomes(shape, position, trigger, outcomes, notes);
    triggerNotes(trigger, position, notes);
    interruptingOutcomes(shape, trigger, outcomes, notes);

    return Behaviour.of(executionKind, savePoint)
        .outcomes(outcomes.toArray(Outcome[]::new))
        .retries(retriesFor(position, trigger))
        .notes(notes.toArray(String[]::new))
        .build();
  }

  private static String executionKindOf(String position, String trigger) {
    return switch (position) {
      // A catch parks until its trigger arrives - except a link, which is a goto resolved at
      // deployment and never waits for anything.
      case "intermediate-catch-event" -> "link".equals(trigger) ? PASS_THROUGH : WAIT_STATE;
      case "intermediate-throw-event" -> "none".equals(trigger) || "link".equals(trigger)
          ? PASS_THROUGH : SYNCHRONOUS;
      // A boundary event is armed when its host starts; no token ever arrives at it.
      case "boundary-event" -> WAIT_STATE;
      case "start-event" -> WAIT_STATE;
      default -> SYNCHRONOUS;
    };
  }

  private static void positionOutcomes(EventShape shape, String position, String trigger,
                                       List<Outcome> outcomes, List<String> notes) {
    switch (position) {
      case "start-event" -> {
        outcomes.add(of(WAITING, "The process definition is deployed",
            "none".equals(trigger)
                ? "Nothing waits: the process is started explicitly by key, by the API or by Tasklist."
                : "The engine registers a subscription and creates an instance whenever the trigger "
                    + "occurs. Nothing is waiting on behalf of any one instance.",
            "The trigger occurs, or a caller starts the process directly."));
        outcomes.add(of(COMPLETED, "An instance is created",
            "The token leaves along the outgoing flow with whatever variables the trigger carried."));
        if (!"none".equals(trigger) && !"compensate".equals(trigger)) {
          outcomes.add(of(STUCK, "The trigger never occurs",
              "No instance is ever created. Nothing is wrong and nothing reports anything, because "
                  + "there is no instance to report about.",
              "Trigger the event, or start the process by key instead."));
        }
      }
      case "intermediate-catch-event" -> {
        if ("link".equals(trigger)) {
          outcomes.add(of(COMPLETED, "A throwing link of the same name fires",
              "Execution jumps here. Resolved at deployment, so it costs nothing at run time."));
          break;
        }
        outcomes.add(of(WAITING, "The token arrives",
            "The engine commits and arms the trigger. The instance parks.",
            "The trigger occurs."));
        outcomes.add(of(COMPLETED, "The trigger occurs",
            "The token leaves along the outgoing flow."));
        outcomes.add(of(STUCK, "The trigger never occurs",
            "The instance waits indefinitely. Nothing times this out on its own.",
            "Trigger the event, or put the wait behind an event-based gateway with a timer branch."));
      }
      case "intermediate-throw-event" -> outcomes.add(of(COMPLETED, "The token arrives",
          switch (trigger) {
            case "none" -> "The engine does nothing. A marker for readers of the diagram.";
            case "link" -> "Execution jumps to the catching link of the same name.";
            case "message" -> "The configured implementation sends the message, then the token leaves.";
            case "signal" -> "The signal is broadcast to every subscriber in the engine, then the "
                + "token leaves.";
            case "escalation" -> "The escalation is raised to the enclosing scope. Non-interrupting "
                + "by nature: this token carries on regardless of whether anything catches it.";
            case "compensate" -> "Compensation handlers registered for already-completed activities "
                + "run, in reverse order, before the token leaves.";
            default -> "The token leaves.";
          }));
      case "boundary-event" -> {
        outcomes.add(of(WAITING, "Its host activity starts",
            "compensate".equals(trigger)
                ? "Nothing is armed and nothing waits. The event registers a compensation handler "
                    + "for the host and takes no further part in normal execution."
                : "The event is armed for as long as the host runs. No token is at the boundary "
                    + "event itself.",
            "The trigger fires, or the host completes first and the event is discarded."));
        if (!"compensate".equals(trigger)) {
          outcomes.add(of(COMPLETED, "The trigger fires while the host is still running",
              "A token is produced on the boundary event's outgoing flow."));
          outcomes.add(of(COMPLETED, "The host completes before the trigger fires",
              "The event is disarmed and discarded. Its outgoing path is never taken - which is "
                  + "the normal case for a timeout that never times out."));
        } else {
          outcomes.add(of(COMPLETED, "Compensation is thrown for a scope containing the host",
              "The handler attached to this boundary event runs. Only for hosts that already "
                  + "completed successfully."));
        }
      }
      case "end-event" -> outcomes.add(of(COMPLETED, "The token arrives",
          switch (trigger) {
            case "none" -> "This token is consumed. The instance ends only once every token has.";
            case "terminate" -> "Every token in the scope is destroyed immediately, including "
                + "parallel branches still doing work. The scope ends at once.";
            case "error" -> "A BpmnError is raised and propagates outward looking for a catcher. "
                + "Uncaught, it fails the instance.";
            case "escalation" -> "An escalation is raised to the enclosing scope. Unlike an error, "
                + "it does not fail the instance when nothing catches it.";
            case "cancel" -> "The enclosing transaction is cancelled, compensation runs for what "
                + "completed, and its cancel boundary event fires.";
            case "message" -> "The configured implementation sends the message, then the token is "
                + "consumed.";
            case "signal" -> "The signal is broadcast engine-wide, then the token is consumed.";
            case "compensate" -> "Compensation runs for already-completed activities before the "
                + "token is consumed.";
            default -> "The token is consumed.";
          }));
      default -> outcomes.add(of(COMPLETED, "The token arrives", "The token leaves."));
    }
  }

  private static void triggerNotes(String trigger, String position, List<String> notes) {
    switch (trigger) {
      case "timer" -> {
        notes.add("A timer is scheduled as a job, so firing depends on the job executor running. "
            + "On a stopped or saturated executor it simply fires late.");
        notes.add("timeCycle on a non-interrupting boundary event repeats: R3/PT1H fires three "
            + "times and produces three tokens. On an interrupting one only the first firing can "
            + "ever happen.");
      }
      case "error" -> notes.add("Catches a BpmnError only - never a technical exception. A delegate "
          + "throwing a RuntimeException produces an incident and does not come this way, which is "
          + "the single most common misunderstanding about error boundary events.");
      case "signal" -> notes.add("Signals are broadcast engine-wide, not to one instance. Every "
          + "subscriber to that signal name reacts, in other processes too.");
      case "message" -> notes.add("Correlation matches on message name plus whatever business key "
          + "or correlation keys the caller supplies. A name matching several waiting instances "
          + "raises an exception in the caller, not in the process.");
      case "conditional" -> notes.add("Evaluated when a variable in scope changes, not on a "
          + "schedule. If nothing ever writes the variable the condition is never re-checked, so a "
          + "condition that is already true when armed does not fire on its own.");
      case "escalation" -> notes.add("Escalation travels outward to an enclosing scope and does not "
          + "fail the instance when nothing catches it, which is the practical difference from an "
          + "error.");
      case "compensate" -> notes.add("Compensation runs only for activities that already completed "
          + "successfully, in reverse order. It never runs during forward flow.");
      case "cancel" -> notes.add("Only meaningful with a transaction sub-process; the parser "
          + "rejects it anywhere else.");
      case "link" -> notes.add("A matched pair inside one process - a throwing link and a catching "
          + "link of the same name. Purely a diagram convenience, resolved at deployment.");
      case "terminate" -> notes.add("Destroys sibling tokens in its own scope only. Inside a "
          + "sub-process it ends that sub-process, not the whole instance.");
      default -> {
      }
    }
  }

  private static void interruptingOutcomes(EventShape shape, String trigger, List<Outcome> outcomes,
                                           List<String> notes) {
    if (shape.interrupting() == null) {
      return;
    }
    if (shape.interrupting()) {
      notes.add("Interrupting: when it fires, the host is cancelled and whatever it was doing is "
          + "abandoned. Work already committed at a save point inside the host is not undone.");
    } else {
      notes.add("Non-interrupting: the host keeps running and a second token proceeds down the "
          + "boundary path in parallel. Both branches are live, so anything downstream must cope "
          + "with being reached twice.");
      outcomes.add(of(COMPLETED, "The trigger fires more than once",
          "Each firing produces another token on the outgoing flow while the host continues. A "
              + "cycle timer therefore multiplies the branch rather than repeating it in place."));
    }
  }

  private static RetryProfile retriesFor(String position, String trigger) {
    if ("intermediate-throw-event".equals(position) && List.of("message", "compensate").contains(trigger)) {
      return RetryProfile.standard();
    }
    if ("end-event".equals(position) && "message".equals(trigger)) {
      return RetryProfile.standard();
    }
    return RetryProfile.none("The event evaluates no user code, so it has no technical failure of "
        + "its own to retry. A timer that cannot fire is a job executor problem, not a retry one.");
  }

  /** Kept separate so a caller can see the incident path exists without an outcome for it. */
  static Outcome jobExecutorIncident() {
    return of(INCIDENT, "A timer job fails to execute",
        "The job retries and then raises an incident, exactly like any other job.",
        "An operator retries the job in Cockpit.");
  }
}
