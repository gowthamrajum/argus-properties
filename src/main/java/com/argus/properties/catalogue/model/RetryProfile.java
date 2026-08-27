package com.argus.properties.catalogue.model;

/**
 * What the engine does with a technical failure here, and how long it takes to become somebody's
 * problem.
 *
 * <p>Worth stating per shape because the answer is not uniform: a {@code BpmnError} is never
 * retried, a synchronous activity with no save point has nothing to retry, and a job's schedule is
 * whatever {@code camunda:failedJobRetryTimeCycle} says. "Time to incident" is a number you can
 * compute from those three facts, and operators care about it a great deal.
 *
 * @param defaultRetries total attempts the engine makes before raising an incident
 * @param configuredBy   the property that overrides the schedule, or null when nothing does
 */
public record RetryProfile(boolean retriesTechnicalFailures,
                           int defaultRetries,
                           String configuredBy,
                           String note) {

  /** Three attempts, back to back, unless a retry cycle spaces them out. */
  public static RetryProfile standard() {
    return new RetryProfile(true, 3, "camunda:failedJobRetryTimeCycle",
        "Three attempts with no delay between them, so an incident appears within milliseconds "
            + "unless camunda:failedJobRetryTimeCycle spaces them out, e.g. R3/PT10M for three "
            + "attempts ten minutes apart. Only applies where a job exists: without an async "
            + "boundary the failure rolls the caller's transaction back instead.");
  }

  public static RetryProfile none(String why) {
    return new RetryProfile(false, 0, null, why);
  }
}
