package com.argus.properties.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.properties.catalogue.model.ListenerEvent;
import com.argus.properties.catalogue.model.ListenerType;
import com.argus.properties.exception.UnknownElementException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListenersTest {

  private final ShapeCatalogue catalogue = new ShapeCatalogue();

  @Test
  void listsBothFamiliesWithEveryEventCamundaSupports() {
    assertThat(events("execution")).containsExactly("start", "end", "take");
    assertThat(events("task"))
        .containsExactly("create", "assignment", "update", "complete", "delete", "timeout");
  }

  /** The event name is the whole configuration, so every one has to say when it actually fires. */
  @Test
  void explainsWhenEachEventFiresAndWhatItIsFor() {
    assertThat(catalogue.listenerTypes()).allSatisfy(type ->
        assertThat(type.events()).allSatisfy(event -> {
          assertThat(event.firesWhen()).as(type.id() + "." + event.event()).isNotBlank();
          assertThat(event.useFor()).as(type.id() + "." + event.event()).isNotBlank();
          assertThat(event.validOn()).as(type.id() + "." + event.event()).isNotEmpty();
        }));
  }

  /** take is the one execution event that is not available on an activity. */
  @Test
  void scopesTakeToSequenceFlowsOnly() {
    ListenerEvent take = event("execution", "take");

    assertThat(take.validOn()).containsExactly("sequence-flow");
    assertThat(take.caveat()).contains("deployment failure");
    assertThat(event("execution", "start").validOn()).contains("user-task", "process", "exclusive-gateway");
  }

  /** Only bpmn:userTask accepts a task listener - the other seven task types do not. */
  @Test
  void scopesEveryTaskEventToUserTasks() {
    assertThat(catalogue.listenerType("task").events())
        .allSatisfy(event -> assertThat(event.validOn()).containsExactly("user-task"));
    assertThat(catalogue.listenerType("task").appliesTo()).contains("User tasks only");
  }

  /**
   * The documented order and community reports disagree, so the catalogue records the dispute
   * rather than picking a side. Quietly asserting one would be worse than saying nothing.
   */
  @Test
  void recordsThatCreateVersusAssignmentOrderIsDisputed() {
    String caveat = event("task", "assignment").caveat();

    assertThat(caveat).contains("after create");
    assertThat(caveat).containsIgnoringCase("opposite");
  }

  @Test
  void recordsWhatTheTimeoutEventNeedsToWorkAtAll() {
    assertThat(event("task", "timeout").caveat())
        .contains("id").contains("timerEventDefinition").contains("job executor");
  }

  @Test
  void saysHowToPointAListenerAtCode() {
    assertThat(catalogue.listenerTypes()).allSatisfy(type ->
        assertThat(type.implementations()).anySatisfy(option ->
            assertThat(option).contains("camunda:delegateExpression")));
  }

  /** The families are reachable from the property that configures them, and vice versa. */
  @Test
  void matchesTheExtensionElementsInTheShapeCatalogue() {
    assertThat(catalogue.listenerType("execution").tag()).isEqualTo("camunda:executionListener");
    assertThat(catalogue.listenerType("task").tag()).isEqualTo("camunda:taskListener");
  }

  @Test
  void refusesAnUnknownFamily() {
    assertThatThrownBy(() -> catalogue.listenerType("execution-listener"))
        .isInstanceOf(UnknownElementException.class)
        .hasMessageContaining("/api/v1/listeners");
  }

  private List<String> events(String typeId) {
    return catalogue.listenerType(typeId).events().stream().map(ListenerEvent::event).toList();
  }

  private ListenerEvent event(String typeId, String name) {
    ListenerType type = catalogue.listenerType(typeId);
    return type.events().stream().filter(e -> e.event().equals(name)).findFirst().orElseThrow();
  }
}
