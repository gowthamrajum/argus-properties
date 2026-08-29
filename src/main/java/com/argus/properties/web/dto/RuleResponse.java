package com.argus.properties.web.dto;

import com.argus.properties.rules.Rule;
import com.argus.properties.rules.RuleKind;
import com.argus.properties.rules.Severity;
import java.time.Instant;

/**
 * A rule as the API returns it.
 *
 * @param shapeName the shape's display name, denormalised from the catalogue so a client listing
 *                  rules does not have to fetch fifty-one shapes to render one column
 */
public record RuleResponse(Long id,
                           String shapeId,
                           String shapeName,
                           String code,
                           RuleKind kind,
                           Severity severity,
                           String title,
                           String rationale,
                           String remediation,
                           boolean enabled,
                           Instant createdAt,
                           Instant updatedAt) {

  public static RuleResponse of(Rule rule, String shapeName) {
    return new RuleResponse(rule.getId(), rule.getShapeId(), shapeName, rule.getCode(),
        rule.getKind(), rule.getSeverity(), rule.getTitle(), rule.getRationale(),
        rule.getRemediation(), rule.isEnabled(), rule.getCreatedAt(), rule.getUpdatedAt());
  }
}
