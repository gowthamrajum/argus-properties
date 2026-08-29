package com.argus.properties.rules;

import com.argus.properties.catalogue.ShapeCatalogue;
import com.argus.properties.exception.ConflictException;
import com.argus.properties.exception.UnknownElementException;
import com.argus.properties.web.dto.RuleRequest;
import com.argus.properties.web.dto.RuleResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD over the authored rules.
 *
 * <p>The interesting part is not the persistence, it is the join to the catalogue: a rule names a
 * shape, and a shape that does not exist means the rule can never fire. Checking it on write turns
 * a silent typo into a 404 at the moment someone can still fix it, which is the same bargain the
 * catalogue's own startup validation makes.
 */
@Service
@Transactional
public class RuleService {

  private final RuleRepository repository;
  private final ShapeCatalogue catalogue;

  public RuleService(RuleRepository repository, ShapeCatalogue catalogue) {
    this.repository = repository;
    this.catalogue = catalogue;
  }

  @Transactional(readOnly = true)
  public List<RuleResponse> search(String shapeId, RuleKind kind, Severity severity, Boolean enabled) {
    if (shapeId != null) {
      // Fail loudly on an unknown shape rather than returning an empty list, which reads as
      // "this shape has no rules" - a different and wrong answer.
      catalogue.shape(shapeId);
    }
    return repository.search(shapeId, kind, severity, enabled).stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public RuleResponse get(long id) {
    return toResponse(require(id));
  }

  @Transactional(readOnly = true)
  public List<RuleResponse> forShape(String shapeId) {
    catalogue.shape(shapeId);
    return repository.findByShapeIdOrderByCodeAsc(shapeId).stream().map(this::toResponse).toList();
  }

  /**
   * @param kind when non-null, pins the kind and ignores whatever the body said - the
   *             {@code /violations} and {@code /findings} paths have already decided
   */
  public RuleResponse create(RuleRequest request, RuleKind kind) {
    RuleKind resolved = resolveKind(request, kind);
    catalogue.shape(request.shapeId());

    if (repository.existsByShapeIdAndCode(request.shapeId(), request.code())) {
      throw new ConflictException("Shape '" + request.shapeId() + "' already has a rule with code '"
          + request.code() + "'. Codes are unique per shape; edit that one or pick another code.");
    }

    Rule saved = repository.save(new Rule(request.shapeId(), request.code(), resolved,
        request.severity(), request.title(), request.rationale(), request.remediation(),
        request.enabledOrDefault()));
    return toResponse(saved);
  }

  public RuleResponse update(long id, RuleRequest request, RuleKind kind) {
    Rule rule = require(id);
    RuleKind resolved = resolveKind(request, kind);

    // The shape and code are the rule's identity. Moving a rule to another shape is a different
    // rule, and silently rewriting it under a caller's nose loses the history of the old one.
    if (!rule.getShapeId().equals(request.shapeId()) || !rule.getCode().equals(request.code())) {
      throw new ConflictException("A rule's shape and code are its identity and cannot be changed. "
          + "Rule " + id + " is " + rule.getShapeId() + "/" + rule.getCode()
          + "; delete it and create the replacement if that is what you meant.");
    }

    rule.apply(resolved, request.severity(), request.title(), request.rationale(),
        request.remediation(), request.enabledOrDefault());
    return toResponse(rule);
  }

  public void delete(long id) {
    repository.delete(require(id));
  }

  private RuleKind resolveKind(RuleRequest request, RuleKind pinned) {
    if (pinned != null) {
      return pinned;
    }
    if (request.kind() == null) {
      throw new UnknownElementException("kind is required on /rules: VIOLATION or FINDING. "
          + "POST to /violations or /findings instead and the path decides it for you.");
    }
    return request.kind();
  }

  private Rule require(long id) {
    return repository.findById(id).orElseThrow(() -> new UnknownElementException(
        "No rule with id " + id + ". GET /api/v1/rules lists them all."));
  }

  private RuleResponse toResponse(Rule rule) {
    return RuleResponse.of(rule, catalogue.shape(rule.getShapeId()).name());
  }
}
