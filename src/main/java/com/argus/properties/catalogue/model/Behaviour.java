package com.argus.properties.catalogue.model;

import java.util.List;

/**
 * What a shape does when a token reaches it.
 *
 * <p>The counterpart to a shape's property list. Properties describe what you may configure;
 * this describes what the engine will then do - every way execution can leave the shape, where the
 * transaction boundary sits, and what happens when something fails.
 *
 * <p>Scope is deliberate: this models <em>engine</em> behaviour, which is deterministic and
 * documented. It says nothing about what a delegate or a script does with the data it is handed,
 * because that is application code the catalogue cannot see. So a profile will say "a technical
 * exception here retries three times and then raises an incident" and stop there.
 *
 * @param executionKind  how the engine treats the shape - see the constants
 * @param savePoint      when the engine commits, which is what decides how far a failure unwinds
 * @param outcomes       every way the token can leave, or fail to
 * @param retries        what happens to a technical failure
 * @param notes          behaviour worth knowing that is not an outcome in itself
 */
public record Behaviour(String executionKind,
                        String savePoint,
                        List<Outcome> outcomes,
                        RetryProfile retries,
                        List<String> notes) {

  /** Runs to completion inside the incoming transaction. */
  public static final String SYNCHRONOUS = "SYNCHRONOUS";

  /** Parks the instance and resumes on an external trigger. Always commits first. */
  public static final String WAIT_STATE = "WAIT_STATE";

  /** Chooses outgoing flows rather than doing work. */
  public static final String ROUTING = "ROUTING";

  /** A pass-through the engine does no work for. */
  public static final String PASS_THROUGH = "PASS_THROUGH";

  /** Synchronous or waiting depending on how the shape is implemented - a service task is both. */
  public static final String IMPLEMENTATION_DEPENDENT = "IMPLEMENTATION_DEPENDENT";

  /** The engine commits here regardless of configuration. Every wait state does. */
  public static final String SAVE_POINT_ALWAYS = "ALWAYS";

  /** Commits only when camunda:asyncBefore or camunda:asyncAfter is set. */
  public static final String SAVE_POINT_ON_ASYNC = "ON_ASYNC";

  /** Never a boundary on its own; failures unwind past it. */
  public static final String SAVE_POINT_NEVER = "NEVER";

  /** Depends on the implementation: an external task always commits, a delegate does not. */
  public static final String SAVE_POINT_IMPLEMENTATION_DEPENDENT = "IMPLEMENTATION_DEPENDENT";

  public Behaviour {
    outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
    notes = notes == null ? List.of() : List.copyOf(notes);
  }

  public static Builder of(String executionKind, String savePoint) {
    return new Builder(executionKind, savePoint);
  }

  public static final class Builder {

    private final String executionKind;
    private final String savePoint;
    private final List<Outcome> outcomes = new java.util.ArrayList<>();
    private RetryProfile retries;
    private List<String> notes = List.of();

    private Builder(String executionKind, String savePoint) {
      this.executionKind = executionKind;
      this.savePoint = savePoint;
    }

    public Builder outcomes(Outcome... declared) {
      this.outcomes.addAll(List.of(declared));
      return this;
    }

    public Builder retries(RetryProfile retries) {
      this.retries = retries;
      return this;
    }

    public Builder notes(String... notes) {
      this.notes = List.of(notes);
      return this;
    }

    public Behaviour build() {
      return new Behaviour(executionKind, savePoint, outcomes, retries, notes);
    }
  }
}
