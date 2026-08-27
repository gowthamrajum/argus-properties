package com.argus.properties.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.properties.catalogue.model.Behaviour;
import com.argus.properties.catalogue.model.Outcome;
import com.argus.properties.catalogue.model.Shape;
import com.argus.properties.exception.UnknownElementException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BehaviourTest {

  private final ShapeCatalogue catalogue = new ShapeCatalogue();
  private final ShapeService service = new ShapeService(catalogue);

  private Behaviour behaviourOf(String id) {
    return catalogue.shape(id).behaviour();
  }

  /**
   * Coverage is pinned deliberately. Adding a shape without a profile should be a visible decision,
   * not something that quietly leaves a gap - so this list only grows when someone edits it.
   */
  @Test
  void coversExactlyTheTaskAndGatewayShapes() {
    Set<String> covered = catalogue.all().stream()
        .filter(shape -> shape.behaviour() != null)
        .map(Shape::id)
        .collect(Collectors.toSet());

    assertThat(covered).containsExactlyInAnyOrder(
        "task", "user-task", "service-task", "send-task", "receive-task", "manual-task",
        "script-task", "business-rule-task",
        "exclusive-gateway", "parallel-gateway", "inclusive-gateway", "event-based-gateway",
        "complex-gateway");
  }

  @Test
  void everyProfileIsComplete() {
    catalogue.all().stream()
        .filter(shape -> shape.behaviour() != null)
        .forEach(shape -> {
          Behaviour behaviour = shape.behaviour();
          assertThat(behaviour.outcomes()).as(shape.id() + " outcomes").isNotEmpty();
          assertThat(behaviour.retries()).as(shape.id() + " retries").isNotNull();
          assertThat(behaviour.retries().note()).as(shape.id() + " retry note").isNotBlank();
          behaviour.outcomes().forEach(outcome -> {
            assertThat(outcome.trigger()).as(shape.id() + "." + outcome.id() + " trigger").isNotBlank();
            assertThat(outcome.effect()).as(shape.id() + "." + outcome.id() + " effect").isNotBlank();
          });
        });
  }

  /** A wait state commits by definition, so marking it async cannot be what creates the boundary. */
  @Test
  void everyWaitStateAlwaysSaves() {
    catalogue.all().stream()
        .filter(shape -> shape.behaviour() != null)
        .filter(shape -> Behaviour.WAIT_STATE.equals(shape.behaviour().executionKind()))
        .forEach(shape -> assertThat(shape.behaviour().savePoint())
            .as(shape.id() + " is a wait state so it must always save")
            .isEqualTo(Behaviour.SAVE_POINT_ALWAYS));
  }

  /** Anything that can park with no trigger coming should say so, and say how to get out. */
  @Test
  void everyStuckOutcomeExplainsHowToRecover() {
    catalogue.all().stream()
        .filter(shape -> shape.behaviour() != null)
        .flatMap(shape -> shape.behaviour().outcomes().stream()
            .filter(outcome -> Outcome.STUCK.equals(outcome.id()))
            .map(outcome -> java.util.Map.entry(shape.id(), outcome)))
        .forEach(entry -> assertThat(entry.getValue().recovery())
            .as(entry.getKey() + " STUCK recovery").isNotBlank());
  }

  /** The deadlock we care about most has to be stated on the shape that causes it. */
  @Test
  void parallelJoinDeclaresTheDeadlock() {
    Behaviour parallel = behaviourOf("parallel-gateway");

    assertThat(parallel.outcomes()).extracting(Outcome::id).contains(Outcome.STUCK);
    assertThat(parallel.outcomes())
        .filteredOn(outcome -> Outcome.STUCK.equals(outcome.id())).singleElement()
        .satisfies(outcome -> {
          assertThat(outcome.trigger()).contains("exclusive split");
          assertThat(outcome.effect()).contains("never finishes");
        });
    assertThat(parallel.retries().retriesTechnicalFailures()).isFalse();
  }

  @Test
  void externalTaskCanBeStuckWithNoWorker() {
    assertThat(behaviourOf("service-task").outcomes())
        .filteredOn(outcome -> Outcome.STUCK.equals(outcome.id())).singleElement()
        .satisfies(outcome -> assertThat(outcome.trigger()).contains("no worker"));
  }

  /** A BpmnError is a modelled failure and must never be described as retryable. */
  @Test
  void bpmnErrorIsNeverRetried() {
    catalogue.all().stream()
        .filter(shape -> shape.behaviour() != null)
        .flatMap(shape -> shape.behaviour().outcomes().stream())
        .filter(outcome -> Outcome.BPMN_ERROR.equals(outcome.id()))
        .forEach(outcome -> assertThat(outcome.effect())
            .as("BPMN_ERROR effect must say it is not retried").contains("no retry"));
  }

  @Test
  void complexGatewayIsUnsupportedAndSaysWhatToUseInstead() {
    Behaviour complex = behaviourOf("complex-gateway");

    assertThat(complex.outcomes()).singleElement().satisfies(outcome -> {
      assertThat(outcome.id()).isEqualTo(Outcome.UNSUPPORTED);
      assertThat(outcome.recovery()).contains("inclusive gateway");
    });
    assertThat(catalogue.shape("complex-gateway").executable()).isFalse();
  }

  /** Nothing to fail means nothing to retry - saying "3 retries" there would be misleading. */
  @Test
  void shapesThatDoNoWorkDeclareNoRetries() {
    List<String> noWork = List.of("receive-task", "parallel-gateway", "event-based-gateway",
        "complex-gateway");

    noWork.forEach(id -> assertThat(behaviourOf(id).retries().retriesTechnicalFailures())
        .as(id + " does no work, so it cannot have a technical failure to retry").isFalse());
  }

  @Test
  void manualTaskIsNotAWaitStateDespiteTheName() {
    assertThat(behaviourOf("manual-task").executionKind()).isEqualTo(Behaviour.PASS_THROUGH);
    assertThat(behaviourOf("manual-task").notes())
        .anyMatch(note -> note.contains("not a wait"));
  }

  @Test
  void serviceTaskIsBothSynchronousAndAWaitStateDependingOnImplementation() {
    Behaviour service = behaviourOf("service-task");

    assertThat(service.executionKind()).isEqualTo(Behaviour.IMPLEMENTATION_DEPENDENT);
    assertThat(service.savePoint()).isEqualTo(Behaviour.SAVE_POINT_IMPLEMENTATION_DEPENDENT);
    assertThat(service.outcomes()).extracting(Outcome::id)
        .contains(Outcome.COMPLETED, Outcome.WAITING, Outcome.BPMN_ERROR, Outcome.INCIDENT,
            Outcome.ROLLBACK, Outcome.STUCK);
  }

  /** "Not catalogued yet" and "does nothing" are different answers. */
  @Test
  void anUncataloguedShapeIsANotFoundRatherThanAnEmptyBody() {
    assertThatThrownBy(() -> service.behaviour("text-annotation"))
        .isInstanceOf(UnknownElementException.class)
        .hasMessageContaining("no behaviour profile yet");
  }
}
