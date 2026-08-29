package com.argus.properties.web;

import com.argus.properties.rules.RuleKind;
import com.argus.properties.rules.RuleService;
import com.argus.properties.rules.Severity;
import com.argus.properties.web.dto.RuleRequest;
import com.argus.properties.web.dto.RuleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD over authored rules, exposed at three paths.
 *
 * <p>{@code /rules} is the whole catalogue and needs a {@code kind} in the body. {@code /violations}
 * and {@code /findings} are the same collection with the kind decided by the path, which is what
 * most callers actually want: you know which sort of thing you are writing before you start
 * writing it, and repeating it in the body is a chance to disagree with yourself.
 *
 * <p>Three doors, one room. There is a single table behind all of it, so a rule created through
 * {@code /violations} is visible at {@code /rules} immediately and cannot drift.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Rules", description = "Authored rules: the one part of this service that is configuration")
public class RuleController {

  private final RuleService service;

  public RuleController(RuleService service) {
    this.service = service;
  }

  // ------------------------------------------------------------------ /rules

  @Operation(summary = "Every authored rule",
      description = "Sorted most severe first. Filters combine: ?shape=user-task&enabled=true.")
  @GetMapping(path = "/rules", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<RuleResponse> rules(
      @Parameter(description = "Catalogue shape id, e.g. user-task.") @RequestParam(required = false) String shape,
      @Parameter(description = "VIOLATION or FINDING.") @RequestParam(required = false) RuleKind kind,
      @Parameter(description = "HIGH, MEDIUM or LOW.") @RequestParam(required = false) Severity severity,
      @Parameter(description = "Omit for both.") @RequestParam(required = false) Boolean enabled) {
    return service.search(shape, kind, severity, enabled);
  }

  @Operation(summary = "One rule")
  @GetMapping(path = "/rules/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public RuleResponse rule(@PathVariable long id) {
    return service.get(id);
  }

  @Operation(summary = "Author a rule",
      description = "kind is required here. The shape must exist in the catalogue, and the code "
          + "must be unique within it.")
  @PostMapping(path = "/rules", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RuleResponse> createRule(@Valid @RequestBody RuleRequest request) {
    return created(service.create(request, null));
  }

  @Operation(summary = "Edit a rule",
      description = "Shape and code are the rule's identity and cannot be changed; everything else can.")
  @PutMapping(path = "/rules/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public RuleResponse updateRule(@PathVariable long id, @Valid @RequestBody RuleRequest request) {
    return service.update(id, request, null);
  }

  @Operation(summary = "Delete a rule",
      description = "Consider setting enabled=false instead: a disabled rule keeps the decision on record.")
  @DeleteMapping("/rules/{id}")
  public ResponseEntity<Void> deleteRule(@PathVariable long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  // ------------------------------------------- /violations and /findings

  @Operation(summary = "Rules that are violations")
  @GetMapping(path = "/violations", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<RuleResponse> violations(@RequestParam(required = false) String shape,
                                       @RequestParam(required = false) Severity severity,
                                       @RequestParam(required = false) Boolean enabled) {
    return service.search(shape, RuleKind.VIOLATION, severity, enabled);
  }

  @Operation(summary = "Author a violation", description = "kind is taken from the path.")
  @PostMapping(path = "/violations", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RuleResponse> createViolation(@Valid @RequestBody RuleRequest request) {
    return created(service.create(request, RuleKind.VIOLATION));
  }

  @Operation(summary = "Edit a violation")
  @PutMapping(path = "/violations/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public RuleResponse updateViolation(@PathVariable long id, @Valid @RequestBody RuleRequest request) {
    return service.update(id, request, RuleKind.VIOLATION);
  }

  @Operation(summary = "Delete a violation")
  @DeleteMapping("/violations/{id}")
  public ResponseEntity<Void> deleteViolation(@PathVariable long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Rules that are findings")
  @GetMapping(path = "/findings", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<RuleResponse> findings(@RequestParam(required = false) String shape,
                                     @RequestParam(required = false) Severity severity,
                                     @RequestParam(required = false) Boolean enabled) {
    return service.search(shape, RuleKind.FINDING, severity, enabled);
  }

  @Operation(summary = "Author a finding", description = "kind is taken from the path.")
  @PostMapping(path = "/findings", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RuleResponse> createFinding(@Valid @RequestBody RuleRequest request) {
    return created(service.create(request, RuleKind.FINDING));
  }

  @Operation(summary = "Edit a finding")
  @PutMapping(path = "/findings/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public RuleResponse updateFinding(@PathVariable long id, @Valid @RequestBody RuleRequest request) {
    return service.update(id, request, RuleKind.FINDING);
  }

  @Operation(summary = "Delete a finding")
  @DeleteMapping("/findings/{id}")
  public ResponseEntity<Void> deleteFinding(@PathVariable long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  // ------------------------------------------------------------ per shape

  @Operation(summary = "Every rule authored against one shape",
      description = "Both kinds, so a shape's page can show its whole policy in one call.")
  @GetMapping(path = "/shapes/{id}/rules", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<RuleResponse> rulesForShape(@PathVariable String id) {
    return service.forShape(id);
  }

  private static ResponseEntity<RuleResponse> created(RuleResponse rule) {
    return ResponseEntity.created(URI.create("/api/v1/rules/" + rule.id())).body(rule);
  }
}
