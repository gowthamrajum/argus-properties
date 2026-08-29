package com.argus.properties.rules;

/**
 * How much a rule firing matters.
 *
 * <p>Deliberately one scale across both kinds. They mean different things, but somebody sorting a
 * dashboard should not have to map two vocabularies onto each other; {@link RuleKind} carries the
 * distinction instead.
 */
public enum Severity {
  /** Breaks deployment, or fails under conditions that occur normally. */
  HIGH,
  /** Fails under plausible but less common conditions, or is genuinely ambiguous. */
  MEDIUM,
  /** Works, but is fragile, unclear, or awkward to operate. */
  LOW
}
