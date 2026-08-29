package com.argus.properties.web.dto;

import com.argus.properties.rules.RuleKind;
import com.argus.properties.rules.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What a caller sends to create or edit a rule.
 *
 * <p>Separate from the entity so the persistence model can change without the contract following
 * it, and so the fields a caller must not set - id, timestamps - simply do not exist here.
 *
 * @param kind omitted on the {@code /violations} and {@code /findings} paths, where the path
 *             already says which it is
 */
public record RuleRequest(
    @NotBlank(message = "shapeId is required")
    String shapeId,

    @NotBlank(message = "code is required")
    @Pattern(regexp = "[A-Z][A-Z0-9_]*",
        message = "code must be SCREAMING_SNAKE_CASE, e.g. USER_TASK_NO_ASSIGNMENT")
    @Size(max = 96)
    String code,

    RuleKind kind,

    @NotNull(message = "severity is required: HIGH, MEDIUM or LOW")
    Severity severity,

    @NotBlank(message = "title is required")
    @Size(max = 200)
    String title,

    @NotBlank(message = "rationale is required - say why this is worth flagging")
    @Size(max = 2000)
    String rationale,

    @NotBlank(message = "remediation is required - say what to change")
    @Size(max = 2000)
    String remediation,

    Boolean enabled) {

  /** New rules are on unless the caller says otherwise; an off-by-default rule helps nobody. */
  public boolean enabledOrDefault() {
    return enabled == null || enabled;
  }
}
