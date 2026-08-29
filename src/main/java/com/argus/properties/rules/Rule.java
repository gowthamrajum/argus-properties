package com.argus.properties.rules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * A rule someone has authored against a shape.
 *
 * <p>The only mutable thing in this service. Everything else - shapes, properties, listeners,
 * concepts - is reference data about BPMN, true regardless of who is asking. A rule is the
 * opposite: it encodes what one team has decided, so it has to be editable at runtime rather than
 * declared in Java and released.
 *
 * <p>A class rather than a record because JPA needs a no-args constructor and mutable fields. The
 * API never exposes it: requests and responses use records, so the persistence model can change
 * without the contract following it.
 */
@Entity
@Table(
    name = "rule_definition",
    uniqueConstraints = @UniqueConstraint(name = "uk_rule_shape_code", columnNames = {"shape_id", "code"}),
    indexes = {@Index(name = "ix_rule_shape", columnList = "shape_id"), @Index(name = "ix_rule_kind", columnList = "kind")})
public class Rule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** The catalogue shape this applies to. Checked on write - a rule about nothing is a typo. */
  @Column(name = "shape_id", nullable = false, length = 64)
  private String shapeId;

  /** Stable identifier for the rule, unique within its shape, e.g. USER_TASK_NO_ASSIGNMENT. */
  @Column(nullable = false, length = 96)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RuleKind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Severity severity;

  @Column(nullable = false, length = 200)
  private String title;

  /** Why this is worth flagging - the reasoning, not the instance. */
  @Column(nullable = false, length = 2000)
  private String rationale;

  /** What to change, concretely. */
  @Column(nullable = false, length = 2000)
  private String remediation;

  /**
   * Disabled rules are kept rather than deleted. A team that decides a rule does not apply usually
   * wants the decision on record, and wants it back when the context changes.
   */
  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Rule() {
    // for JPA
  }

  public Rule(String shapeId, String code, RuleKind kind, Severity severity, String title,
              String rationale, String remediation, boolean enabled) {
    this.shapeId = shapeId;
    this.code = code;
    this.kind = kind;
    this.severity = severity;
    this.title = title;
    this.rationale = rationale;
    this.remediation = remediation;
    this.enabled = enabled;
  }

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  /** Applies an edit in place. The code and the shape are identity, so neither moves. */
  public void apply(RuleKind kind, Severity severity, String title, String rationale,
                    String remediation, boolean enabled) {
    this.kind = kind;
    this.severity = severity;
    this.title = title;
    this.rationale = rationale;
    this.remediation = remediation;
    this.enabled = enabled;
  }

  public Long getId() {
    return id;
  }

  public String getShapeId() {
    return shapeId;
  }

  public String getCode() {
    return code;
  }

  public RuleKind getKind() {
    return kind;
  }

  public Severity getSeverity() {
    return severity;
  }

  public String getTitle() {
    return title;
  }

  public String getRationale() {
    return rationale;
  }

  public String getRemediation() {
    return remediation;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
