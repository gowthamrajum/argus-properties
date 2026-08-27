package com.argus.properties.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.properties.catalogue.model.Behaviour;
import com.argus.properties.catalogue.model.EventShape;
import com.argus.properties.catalogue.model.Outcome;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EventShapeTest {

  private final ShapeCatalogue catalogue = new ShapeCatalogue();
  private final ShapeService service = new ShapeService(catalogue);

  private EventShape shape(String id) {
    return catalogue.eventShape(id);
  }

  /** Pinned so a change to the matrix shows up as a number moving, not as a silent reshuffle. */
  @Test
  void derivesFortyNineConcreteShapesFromFiveTags() {
    Map<String, Long> byPosition = catalogue.eventShapes().stream()
        .collect(Collectors.groupingBy(EventShape::positionShapeId, Collectors.counting()));

    assertThat(catalogue.eventShapes()).hasSize(49);
    assertThat(byPosition).containsExactlyInAnyOrderEntriesOf(Map.of(
        "start-event", 17L,
        "intermediate-catch-event", 5L,
        "intermediate-throw-event", 6L,
        "boundary-event", 13L,
        "end-event", 8L));
  }

  @Test
  void boundaryEventsCoverEightDefinitionsAndTheInterruptingAxis() {
    List<EventShape> boundary = service.eventShapes("boundary-event", null, null);

    assertThat(boundary).hasSize(13);
    assertThat(boundary).filteredOn(s -> Boolean.FALSE.equals(s.interrupting())).hasSize(5);
    assertThat(boundary).extracting(EventShape::definitionShapeId).containsExactlyInAnyOrder(
        "message-event-definition", "message-event-definition",
        "timer-event-definition", "timer-event-definition",
        "signal-event-definition", "signal-event-definition",
        "conditional-event-definition", "conditional-event-definition",
        "escalation-event-definition", "escalation-event-definition",
        "error-event-definition", "cancel-event-definition", "compensate-event-definition");
  }

  /** An error always cancels its host, so a non-interrupting form must not be derivable. */
  @Test
  void errorAndCancelBoundariesHaveNoNonInterruptingForm() {
    assertThat(service.eventShapes("boundary-event", "error-event-definition", null))
        .singleElement().extracting(EventShape::interrupting).isEqualTo(Boolean.TRUE);
    assertThat(service.eventShapes("boundary-event", "cancel-event-definition", null))
        .singleElement().satisfies(shape -> {
          assertThat(shape.interrupting()).isTrue();
          assertThat(shape.requires()).anyMatch(r -> r.contains("transaction"));
        });
  }

  /** Compensation neither interrupts nor runs in parallel - the axis simply does not apply. */
  @Test
  void compensateBoundaryHasNoInterruptingFlagAtAll() {
    assertThat(shape("compensate-boundary-event").interrupting()).isNull();
  }

  @Test
  void refusesToDeriveIllegalPairings() {
    List<String> ids = catalogue.eventShapes().stream().map(EventShape::id).toList();

    assertThat(ids)
        .doesNotContain("timer-end-event")           // you cannot throw the passage of time
        .doesNotContain("link-boundary-event")       // a link is a matched pair, nothing else
        .doesNotContain("terminate-boundary-event")  // terminate only ends
        .doesNotContain("conditional-end-event")
        .doesNotContain("error-intermediate-catch-event");
  }

  @Test
  void answersALegalPairingWithTheShapesItProduces() {
    ShapeService.LegalityAnswer answer =
        service.check("boundary-event", "timer-event-definition", null);

    assertThat(answer.legal()).isTrue();
    assertThat(answer.shapeIds())
        .containsExactlyInAnyOrder("timer-boundary-event", "non-interrupting-timer-boundary-event");
  }

  /** A bare "no" leaves the caller guessing, so a rejection lists what the position does take. */
  @Test
  void answersAnIllegalPairingWithWhatIsAccepted() {
    ShapeService.LegalityAnswer answer = service.check("end-event", "timer-event-definition", null);

    assertThat(answer.legal()).isFalse();
    assertThat(answer.reason())
        .contains("end-event does not accept timer-event-definition")
        .contains("terminate-event-definition");
  }

  @Test
  void everyDerivedShapeHasComposedBehaviourThatSaysHowExecutionProceeds() {
    catalogue.eventShapes().forEach(shape -> {
      assertThat(shape.behaviour()).as(shape.id()).isNotNull();
      assertThat(shape.behaviour().outcomes()).as(shape.id() + " outcomes")
          .extracting(Outcome::id).contains(Outcome.COMPLETED);
      assertThat(shape.behaviour().retries()).as(shape.id() + " retries").isNotNull();
    });
  }

  /** The behavioural difference that most justifies splitting the shapes apart. */
  @Test
  void aNonInterruptingCycleTimerMultipliesTheBranch() {
    Behaviour repeating = shape("non-interrupting-timer-boundary-event").behaviour();
    Behaviour once = shape("timer-boundary-event").behaviour();

    assertThat(repeating.outcomes())
        .anyMatch(outcome -> outcome.trigger().contains("fires more than once"));
    assertThat(repeating.notes()).anyMatch(note -> note.contains("in parallel"));

    assertThat(once.outcomes())
        .noneMatch(outcome -> outcome.trigger().contains("fires more than once"));
    assertThat(once.notes()).anyMatch(note -> note.contains("cancelled"));
  }

  /** The most common misunderstanding in Camunda 7 deserves to be stated on the shape. */
  @Test
  void errorBoundaryStatesThatItDoesNotCatchTechnicalExceptions() {
    assertThat(shape("error-boundary-event").behaviour().notes())
        .anyMatch(note -> note.contains("never a technical exception"));
  }

  @Test
  void aBoundaryEventIsAWaitStateArmedByItsHostRatherThanReachedByAToken() {
    Behaviour timer = shape("timer-boundary-event").behaviour();

    assertThat(timer.executionKind()).isEqualTo(Behaviour.WAIT_STATE);
    assertThat(timer.outcomes())
        .anyMatch(outcome -> outcome.trigger().contains("host activity starts"))
        .anyMatch(outcome -> outcome.trigger().contains("host completes before"));
  }

  /** A link never waits: it is resolved at deployment, so calling it a wait state would mislead. */
  @Test
  void linkEventsAreResolvedAtDeploymentAndDoNotWait() {
    assertThat(shape("link-intermediate-catch-event").behaviour().executionKind())
        .isEqualTo(Behaviour.PASS_THROUGH);
    assertThat(shape("link-intermediate-throw-event").behaviour().executionKind())
        .isEqualTo(Behaviour.PASS_THROUGH);
  }

  @Test
  void startEventsInAnEventSubProcessAcceptMoreThanTopLevelOnes() {
    List<String> topLevel = service.eventShapes("start-event", null, null).stream()
        .filter(s -> "TOP_LEVEL".equals(s.context())).map(EventShape::definitionShapeId).toList();
    List<String> inSubProcess = service.eventShapes("start-event", null, null).stream()
        .filter(s -> "EVENT_SUB_PROCESS".equals(s.context())).map(EventShape::definitionShapeId).toList();

    assertThat(topLevel).doesNotContain("error-event-definition", "compensate-event-definition");
    assertThat(inSubProcess).contains("error-event-definition", "compensate-event-definition");
  }

  @Test
  void theXmlSketchMarksOnlyTheNonInterruptingForm() {
    assertThat(shape("non-interrupting-timer-boundary-event").xmlSketch())
        .contains("cancelActivity=\"false\"")
        .contains("<bpmn:timerEventDefinition />");
    assertThat(shape("timer-boundary-event").xmlSketch()).doesNotContain("cancelActivity");
  }
}
