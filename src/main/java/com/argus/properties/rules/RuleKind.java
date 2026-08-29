package com.argus.properties.rules;

/**
 * What a rule produces when it fires.
 *
 * <p>One authored catalogue, two outcomes. They are stored together because everything about
 * declaring a rule - which shape, what severity, why it matters, what to do about it - is
 * identical; only what you call the result differs. Splitting them into two tables would double
 * the schema to record one word.
 */
public enum RuleKind {
  /** Modelling hygiene: the model is legal but ill-advised. */
  FINDING,
  /** A breach of a standard the team has committed to. */
  VIOLATION
}
