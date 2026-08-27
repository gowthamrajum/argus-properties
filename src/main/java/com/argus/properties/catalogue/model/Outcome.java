package com.argus.properties.catalogue.model;

/**
 * One way a token can leave a shape - or fail to.
 *
 * <p>The outcome set is what makes a shape analysable. A property list says what you can configure;
 * this says what actually happens when the engine reaches the shape, which is what determines
 * whether an instance completes, stalls, or ends up in front of an operator.
 *
 * @param trigger  the condition that produces this outcome
 * @param effect   what the engine does, in terms an operator would recognise
 * @param recovery how an instance gets out of this state, or null when it needs no recovery
 */
public record Outcome(String id, String trigger, String effect, String recovery) {

  /** The token leaves along an outgoing sequence flow. The only outcome that is simply progress. */
  public static final String COMPLETED = "COMPLETED";

  /** The instance parks here and resumes when something external happens. */
  public static final String WAITING = "WAITING";

  /**
   * A {@code BpmnError} is raised. Modelled failure: catchable by an error boundary event or an
   * error event sub-process, and never retried.
   */
  public static final String BPMN_ERROR = "BPMN_ERROR";

  /**
   * A technical failure that has exhausted its retries. The job stays put, the instance is stuck,
   * and an operator has to intervene. Visible in Cockpit.
   */
  public static final String INCIDENT = "INCIDENT";

  /**
   * A technical failure with no save point between here and the caller, so the whole transaction
   * unwinds. No incident is created and nothing is left to retry - the work simply did not happen.
   */
  public static final String ROLLBACK = "ROLLBACK";

  /**
   * Parked with no trigger that can ever arrive - no worker on the topic, no message coming, no
   * timer to fire. The dangerous one: no error, no incident, nothing in the logs. Instances
   * accumulate here silently.
   */
  public static final String STUCK = "STUCK";

  /** The engine has no implementation for the shape, so a model containing it will not deploy. */
  public static final String UNSUPPORTED = "UNSUPPORTED";

  public static Outcome of(String id, String trigger, String effect) {
    return new Outcome(id, trigger, effect, null);
  }

  public static Outcome of(String id, String trigger, String effect, String recovery) {
    return new Outcome(id, trigger, effect, recovery);
  }
}
