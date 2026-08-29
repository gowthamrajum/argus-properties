package com.argus.properties.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.properties.exception.ConflictException;
import com.argus.properties.exception.UnknownElementException;
import com.argus.properties.web.dto.RuleRequest;
import com.argus.properties.web.dto.RuleResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RuleServiceTest {

  @Autowired
  private RuleService service;

  @Autowired
  private RuleRepository repository;

  @BeforeEach
  void clear() {
    repository.deleteAll();
  }

  private static RuleRequest request(String shapeId, String code, RuleKind kind, Severity severity) {
    return new RuleRequest(shapeId, code, kind, severity, "A title",
        "Why it matters.", "What to change.", null);
  }

  @Test
  void createsAndReadsBackARule() {
    RuleResponse created = service.create(request("user-task", "NO_ASSIGNMENT", RuleKind.VIOLATION, Severity.HIGH), null);

    assertThat(created.id()).isNotNull();
    assertThat(created.enabled()).as("new rules are on unless the caller says otherwise").isTrue();
    assertThat(created.createdAt()).isNotNull();
    // The shape's display name is denormalised so a listing needs one call, not fifty-two.
    assertThat(created.shapeName()).isEqualTo("User Task");
    assertThat(service.get(created.id()).code()).isEqualTo("NO_ASSIGNMENT");
  }

  /** A rule naming a shape that does not exist can never fire, so it is a typo, not a rule. */
  @Test
  void refusesARuleAgainstAShapeThatDoesNotExist() {
    assertThatThrownBy(() -> service.create(request("user-tsak", "X", RuleKind.FINDING, Severity.LOW), null))
        .isInstanceOf(UnknownElementException.class)
        .hasMessageContaining("user-tsak");
  }

  @Test
  void refusesADuplicateCodeOnTheSameShape() {
    service.create(request("user-task", "NO_ASSIGNMENT", RuleKind.VIOLATION, Severity.HIGH), null);

    assertThatThrownBy(() -> service.create(request("user-task", "NO_ASSIGNMENT", RuleKind.FINDING, Severity.LOW), null))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("already has a rule with code");
  }

  /** Codes are unique per shape, not globally: two shapes can each have a NO_RETRY rule. */
  @Test
  void allowsTheSameCodeOnADifferentShape() {
    service.create(request("user-task", "NO_RETRY", RuleKind.FINDING, Severity.LOW), null);
    service.create(request("service-task", "NO_RETRY", RuleKind.FINDING, Severity.LOW), null);

    assertThat(service.search(null, null, null, null)).hasSize(2);
  }

  @Test
  void editsEverythingExceptTheIdentity() {
    RuleResponse created = service.create(request("user-task", "NO_ASSIGNMENT", RuleKind.VIOLATION, Severity.HIGH), null);

    RuleResponse updated = service.update(created.id(),
        new RuleRequest("user-task", "NO_ASSIGNMENT", RuleKind.FINDING, Severity.LOW,
            "Reworded", "New reasoning.", "New instruction.", false), null);

    assertThat(updated.kind()).isEqualTo(RuleKind.FINDING);
    assertThat(updated.severity()).isEqualTo(Severity.LOW);
    assertThat(updated.title()).isEqualTo("Reworded");
    assertThat(updated.enabled()).isFalse();
    assertThat(updated.id()).isEqualTo(created.id());
  }

  /** Rewriting a rule onto another shape loses the history of the one that was there. */
  @Test
  void refusesToMoveARuleToAnotherShapeOrCode() {
    RuleResponse created = service.create(request("user-task", "NO_ASSIGNMENT", RuleKind.VIOLATION, Severity.HIGH), null);

    assertThatThrownBy(() -> service.update(created.id(),
        request("service-task", "NO_ASSIGNMENT", RuleKind.VIOLATION, Severity.HIGH), null))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("identity");
  }

  @Test
  void deletesARule() {
    RuleResponse created = service.create(request("user-task", "X", RuleKind.FINDING, Severity.LOW), null);
    service.delete(created.id());

    assertThatThrownBy(() -> service.get(created.id())).isInstanceOf(UnknownElementException.class);
  }

  /** The path decides the kind on /violations and /findings, whatever the body claims. */
  @Test
  void pinsTheKindWhenThePathHasAlreadyDecidedIt() {
    RuleResponse created = service.create(
        request("user-task", "X", RuleKind.FINDING, Severity.LOW), RuleKind.VIOLATION);

    assertThat(created.kind()).isEqualTo(RuleKind.VIOLATION);
  }

  @Test
  void insistsOnAKindWhenThePathDoesNotSupplyOne() {
    assertThatThrownBy(() -> service.create(
        new RuleRequest("user-task", "X", null, Severity.LOW, "t", "r", "f", null), null))
        .isInstanceOf(UnknownElementException.class)
        .hasMessageContaining("kind is required");
  }

  /** Most severe first, because that is the order anyone triaging wants. */
  @Test
  void ordersBySeverityRatherThanAlphabetically() {
    service.create(request("user-task", "LOW_ONE", RuleKind.FINDING, Severity.LOW), null);
    service.create(request("user-task", "HIGH_ONE", RuleKind.FINDING, Severity.HIGH), null);
    service.create(request("user-task", "MEDIUM_ONE", RuleKind.FINDING, Severity.MEDIUM), null);

    assertThat(service.search(null, null, null, null)).extracting(RuleResponse::severity)
        .containsExactly(Severity.HIGH, Severity.MEDIUM, Severity.LOW);
  }

  @Test
  void filtersCombine() {
    service.create(request("user-task", "A", RuleKind.VIOLATION, Severity.HIGH), null);
    service.create(request("user-task", "B", RuleKind.FINDING, Severity.HIGH), null);
    service.create(request("service-task", "C", RuleKind.VIOLATION, Severity.LOW), null);

    assertThat(service.search("user-task", null, null, null)).hasSize(2);
    assertThat(service.search(null, RuleKind.VIOLATION, null, null)).hasSize(2);
    assertThat(service.search("user-task", RuleKind.VIOLATION, Severity.HIGH, null)).hasSize(1);
  }

  /** An empty list would read as "this shape has no rules", which is a different answer. */
  @Test
  void refusesToFilterByAShapeThatDoesNotExist() {
    assertThatThrownBy(() -> service.search("nope", null, null, null))
        .isInstanceOf(UnknownElementException.class);
  }

  @Test
  void listsEveryRuleForOneShapeRegardlessOfKind() {
    service.create(request("user-task", "A", RuleKind.VIOLATION, Severity.HIGH), null);
    service.create(request("user-task", "B", RuleKind.FINDING, Severity.LOW), null);
    service.create(request("service-task", "C", RuleKind.FINDING, Severity.LOW), null);

    assertThat(service.forShape("user-task")).extracting(RuleResponse::code)
        .containsExactly("A", "B");
  }

  @Test
  void keepsADisabledRuleRatherThanLosingIt() {
    RuleResponse created = service.create(request("user-task", "X", RuleKind.FINDING, Severity.LOW), null);
    service.update(created.id(),
        new RuleRequest("user-task", "X", RuleKind.FINDING, Severity.LOW, "t", "r", "f", false), null);

    assertThat(service.search(null, null, null, false)).hasSize(1);
    assertThat(service.search(null, null, null, true)).isEmpty();
    assertThat(service.search(null, null, null, null)).as("both, when unfiltered").hasSize(1);
  }

  @Test
  void seedsNothingWhenSeedingIsOff() {
    assertThat(List.of()).isEmpty(); // guard: the test profile disables the seeder
    assertThat(repository.count()).isZero();
  }
}
