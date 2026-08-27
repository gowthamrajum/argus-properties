package com.argus.properties.catalogue;

import static com.argus.properties.catalogue.model.Behaviour.ROUTING;
import static com.argus.properties.catalogue.model.Behaviour.SAVE_POINT_ALWAYS;
import static com.argus.properties.catalogue.model.Behaviour.SAVE_POINT_NEVER;
import static com.argus.properties.catalogue.model.Behaviour.SAVE_POINT_ON_ASYNC;
import static com.argus.properties.catalogue.model.Behaviour.WAIT_STATE;
import static com.argus.properties.catalogue.model.Outcome.COMPLETED;
import static com.argus.properties.catalogue.model.Outcome.INCIDENT;
import static com.argus.properties.catalogue.model.Outcome.ROLLBACK;
import static com.argus.properties.catalogue.model.Outcome.STUCK;
import static com.argus.properties.catalogue.model.Outcome.UNSUPPORTED;
import static com.argus.properties.catalogue.model.Outcome.WAITING;
import static com.argus.properties.catalogue.model.Outcome.of;

import com.argus.properties.catalogue.model.Behaviour;
import com.argus.properties.catalogue.model.RetryProfile;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What each gateway does at run time.
 *
 * <p>Gateways do no work, so they have no interesting failure modes of their own. What they have
 * instead is routing that can decline to route: a split where nothing matches throws, and a join
 * that waits for a branch which cannot arrive waits forever. Both look nothing like an error while
 * they are happening, which is why they are worth stating precisely.
 */
final class GatewayBehaviours {

  private GatewayBehaviours() {
  }

  /** Selecting no outgoing flow is a run-time exception, not a quiet no-op. */
  private static com.argus.properties.catalogue.model.Outcome noFlowSelected(String gateway) {
    return of(INCIDENT,
        "No outgoing condition evaluates true and no default flow is set",
        "The engine throws: \"No outgoing sequence flow for the element with id '...' could be "
            + "selected for continuing the process.\" With a job in front of it this becomes an "
            + "incident; without one it unwinds the caller's transaction instead.",
        "Set a default flow, or widen the conditions so they cannot all be false. An operator "
            + "cannot fix an instance already stuck here without changing the model.");
  }

  private static com.argus.properties.catalogue.model.Outcome conditionThrew() {
    return of(ROLLBACK,
        "A condition expression throws - typically a variable it reads does not exist",
        "Evaluation happens as the token arrives, so the failure belongs to the transaction that "
            + "brought it here and unwinds with it, unless the gateway is asynchronous.",
        "Guard the expression, or make sure the variable is always set upstream.");
  }

  static Map<String, Behaviour> all() {
    Map<String, Behaviour> behaviours = new LinkedHashMap<>();

    behaviours.put("exclusive-gateway", Behaviour.of(ROUTING, SAVE_POINT_ON_ASYNC)
        .outcomes(
            of(COMPLETED, "Exactly one outgoing condition is true, or none are and a default exists",
                "The token takes that one flow. Conditions are evaluated in document order and the "
                    + "first true one wins; the rest are not evaluated."),
            of(COMPLETED, "Several outgoing conditions are true",
                "Still exactly one flow is taken - whichever came first in the XML. No warning is "
                    + "issued, so overlapping conditions behave deterministically but for a reason "
                    + "nobody reading the diagram can see.",
                "Make the conditions mutually exclusive, so the outcome does not depend on element "
                    + "order in the file."),
            noFlowSelected("exclusive gateway"),
            conditionThrew())
        .retries(RetryProfile.standard())
        .notes("A join is free: any incoming token passes straight through, with no waiting and no "
                + "merging. An exclusive gateway used as a join therefore fires once per arriving "
                + "token, which duplicates everything downstream if two branches were active.",
            "Reordering sequence flows in the XML can change which branch wins. Modellers do not "
                + "expect file order to be semantic.")
        .build());

    behaviours.put("parallel-gateway", Behaviour.of(ROUTING, SAVE_POINT_ON_ASYNC)
        .outcomes(
            of(COMPLETED, "Splitting: the token arrives",
                "One token is produced on every outgoing flow, unconditionally. Conditions on those "
                    + "flows are ignored - not evaluated and not warned about."),
            of(COMPLETED, "Joining: a token has arrived on every incoming flow",
                "The tokens are merged into one and it continues."),
            of(WAITING, "Joining: some but not all incoming flows have delivered a token",
                "The arrived tokens are held at the gateway. This is normal and expected while "
                    + "sibling branches are still running.",
                "The remaining branches complete."),
            of(STUCK, "Joining: an incoming branch can never deliver a token, because it sits "
                    + "behind an exclusive split that chose a different path",
                "The join waits forever. No error, no incident, nothing in the logs - the instance "
                    + "is simply active and never finishes. The classic BPMN deadlock.",
                "None at run time. The model has to change; existing instances have to be "
                    + "cancelled or migrated."))
        .retries(RetryProfile.none("A parallel gateway evaluates nothing, so it has no technical "
            + "failure mode to retry."))
        .notes("It has no default flow and honours no conditions. A conditional flow leaving a "
                + "parallel gateway is a silent no-op and a common source of branches that always "
                + "run.",
            "A join counts incoming sequence flows, not active branches. This is what makes the "
                + "exclusive-split-into-parallel-join deadlock possible.")
        .build());

    behaviours.put("inclusive-gateway", Behaviour.of(ROUTING, SAVE_POINT_ON_ASYNC)
        .outcomes(
            of(COMPLETED, "Splitting: one or more conditions are true",
                "A token is produced on every flow whose condition holds - one, several, or all."),
            of(COMPLETED, "Joining: every branch that could still arrive has arrived",
                "The engine reasons about which upstream branches remain active, rather than "
                    + "counting incoming flows, and merges when none can still deliver."),
            of(WAITING, "Joining: a branch upstream is still running",
                "Arrived tokens wait.",
                "The remaining active branches complete."),
            noFlowSelected("inclusive gateway"),
            conditionThrew())
        .retries(RetryProfile.standard())
        .notes("The join is the expensive part: deciding whether a token can still arrive means "
                + "walking backwards through the process graph, and it degrades noticeably on wide "
                + "or heavily nested models.",
            "Because the join reasons about reachability rather than counting, it does not deadlock "
                + "the way a parallel join does - which is often the right fix for one that has.")
        .build());

    behaviours.put("event-based-gateway", Behaviour.of(WAIT_STATE, SAVE_POINT_ALWAYS)
        .outcomes(
            of(WAITING, "The token arrives",
                "The engine commits, then arms every event that follows the gateway at once - "
                    + "message subscriptions, timers, signals. The instance parks.",
                "Whichever armed event occurs first."),
            of(COMPLETED, "One of the following events occurs",
                "That branch is taken and every other armed event is cancelled, including any "
                    + "timers. Exactly one branch always wins."),
            of(STUCK, "None of the following events ever occurs",
                "The instance waits indefinitely. Only avoidable by making one of the branches a "
                    + "timer, which is the whole point of the shape.",
                "Trigger one of the events, or include a timer branch so the race always has a "
                    + "loser that finishes."))
        .retries(RetryProfile.none("The gateway itself evaluates nothing. Failures belong to the "
            + "events that follow it."))
        .notes("This is the idiomatic way to put a timeout on waiting for a message: one branch "
                + "carries the message, the other a timer.",
            "It must be followed directly by catching events or receive tasks. Anything else is "
                + "rejected at deployment.",
            "Unlike every other gateway it is a wait state, so it always commits - it is a "
                + "transaction boundary whether or not you mark it asynchronous.")
        .build());

    behaviours.put("complex-gateway", Behaviour.of(ROUTING, SAVE_POINT_NEVER)
        .outcomes(
            of(UNSUPPORTED, "A model containing one is deployed",
                "Camunda 7 implements no execution behaviour for this gateway. It exists in the "
                    + "BPMN specification and every modeller draws it, but the engine will not run "
                    + "it.",
                "Replace it with an inclusive gateway, which covers nearly every case a complex "
                    + "gateway is reached for."))
        .retries(RetryProfile.none("Never executes, so there is nothing to retry."))
        .notes("Catalogued because modellers offer it and people use it, not because it works. "
                + "Finding out at deployment is the usual way people discover this.")
        .build());

    return behaviours;
  }
}
