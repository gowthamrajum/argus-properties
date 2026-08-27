package com.argus.properties.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.properties.catalogue.model.Concept;
import com.argus.properties.catalogue.model.Property;
import com.argus.properties.catalogue.model.PropertyLabels;
import com.argus.properties.catalogue.model.Shape;
import com.argus.properties.exception.UnknownElementException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards that make the catalogue authoritative rather than approximately right.
 *
 * <p>Reference data fails quietly - a missing property or a wrong default is served with the same
 * confidence as a correct one - so the checks here are about internal consistency, which is the
 * part a machine can verify.
 */
class ShapeCatalogueTest {

  private final ShapeCatalogue catalogue = new ShapeCatalogue();

  @Test
  void validatesCleanlyAtStartup() {
    // The same check @PostConstruct runs: unknown inherited groups, missing summaries, duplicate
    // or undescribed properties. Running it here reports the problem against a test, not a boot.
    catalogue.validate();
  }

  @Test
  void coversEveryPaletteCategory() {
    assertThat(catalogue.all()).extracting(Shape::category).contains(
        Shape.CONTAINER, Shape.ACTIVITY, Shape.GATEWAY, Shape.EVENT,
        Shape.EVENT_DEFINITION, Shape.DATA, Shape.ARTIFACT, Shape.CONNECTION);
  }

  /**
   * BPMN's own supertype: an activity is the thing that can loop, be compensated, carry data
   * associations and be interrupted by a boundary event. A call activity does all of that, so it
   * belongs here rather than with the containers it merely resembles on the canvas.
   */
  @Test
  void filesEverythingThatDoesWorkUnderActivities() {
    List<String> activities = catalogue.all().stream()
        .filter(shape -> shape.category().equals(Shape.ACTIVITY)).map(Shape::id).toList();

    assertThat(activities).containsExactlyInAnyOrder("task", "user-task", "service-task",
        "send-task", "receive-task", "manual-task", "script-task", "business-rule-task",
        "call-activity");
    assertThat(catalogue.shape("call-activity").category()).isEqualTo(Shape.ACTIVITY);
    // Sub-processes stay containers: they hold other shapes, which call activities do not.
    assertThat(catalogue.shape("sub-process").category()).isEqualTo(Shape.CONTAINER);
  }

  @Test
  void namesEachFamilyInThePluralBecauseTheyAreFamilies() {
    assertThat(Shape.labelFor(Shape.ACTIVITY)).isEqualTo("Activities");
    assertThat(Shape.labelFor(Shape.GATEWAY)).isEqualTo("Gateways");
    // Declared rather than derived, so these two do not come out mangled.
    assertThat(Shape.labelFor(Shape.DATA)).isEqualTo("Data");
    assertThat(Shape.labelFor(Shape.EVENT_DEFINITION)).isEqualTo("Event definitions");
  }

  @Test
  void hasNoDuplicateShapeIds() {
    assertThat(catalogue.all()).extracting(Shape::id).doesNotHaveDuplicates();
  }

  /** Two shapes may legitimately share a tag - a sub-process and an event sub-process do. */
  @Test
  void allowsTwoShapesToShareATag() {
    List<Shape> subProcesses = catalogue.all().stream()
        .filter(shape -> shape.tag().equals("bpmn:subProcess")).toList();

    assertThat(subProcesses).extracting(Shape::id)
        .containsExactlyInAnyOrder("sub-process", "event-sub-process");
  }

  @Test
  void resolvesInheritedPropertiesBeforeOwnAndStampsTheirOrigin() {
    Shape userTask = catalogue.shape("user-task");
    List<Property> effective = catalogue.effectiveProperties(userTask);

    assertThat(effective).hasSizeGreaterThan(userTask.properties().size());
    assertThat(effective.get(0).name()).isEqualTo("id");
    assertThat(effective.get(0).inheritedFrom()).isEqualTo(PropertyGroups.BASE_ELEMENT);
    assertThat(effective).filteredOn(property -> property.name().equals("camunda:assignee"))
        .singleElement()
        .satisfies(property -> assertThat(property.inheritedFrom()).isNull());
  }

  @Test
  void carriesTheAsyncAttributesOnEveryFlowNodeThatCanBeMadeAsync() {
    List<String> asyncCapable = List.of("user-task", "service-task", "sub-process",
        "call-activity", "exclusive-gateway", "start-event", "boundary-event");

    assertThat(asyncCapable).allSatisfy(id ->
        assertThat(catalogue.effectiveProperties(catalogue.shape(id)))
            .as(id + " should inherit camunda:asyncBefore")
            .extracting(Property::name).contains("camunda:asyncBefore"));
  }

  /**
   * Execution listeners are inherited, so they are absent from a shape's own property list and
   * present in its effective one. That split is easy to get wrong in a way no other test notices.
   */
  @Test
  void exposesExecutionListenersOnEveryFlowNodeThatSupportsThem() {
    List<String> supported = List.of("user-task", "service-task", "script-task", "sub-process",
        "call-activity", "exclusive-gateway", "parallel-gateway", "event-based-gateway",
        "start-event", "boundary-event", "end-event", "intermediate-catch-event");

    assertThat(supported).allSatisfy(id -> assertThat(catalogue.effectiveProperties(catalogue.shape(id)))
        .as(id + " should expose camunda:executionListener")
        .extracting(Property::name).contains("camunda:executionListener"));

    // Declared directly on these two rather than inherited: a process is not a flow node, and a
    // sequence flow takes event="take" instead of start/end.
    assertThat(catalogue.shape("process").properties()).extracting(Property::name)
        .contains("camunda:executionListener");
    assertThat(catalogue.shape("sequence-flow").properties()).extracting(Property::name)
        .contains("camunda:executionListener");
  }

  @Test
  void keepsListenersOutOfTheSchedulingGroup() {
    assertThat(catalogue.group(PropertyGroups.CAMUNDA_ASYNC).properties())
        .extracting(Property::name)
        .contains("camunda:asyncBefore", "camunda:failedJobRetryTimeCycle")
        .doesNotContain("camunda:executionListener", "camunda:inputOutput");
    assertThat(catalogue.group(PropertyGroups.CAMUNDA_EXTENSIONS).properties())
        .extracting(Property::name)
        .containsExactly("camunda:executionListener", "camunda:properties");
    // inputOutput sits in its own group: its membership is a specific set of elements, not a
    // family, so it cannot ride along with the extensions every flow node accepts.
    assertThat(catalogue.group(PropertyGroups.CAMUNDA_IO_MAPPING).properties())
        .extracting(Property::name).containsExactly("camunda:inputOutput");
  }

  /**
   * Pinned against the engine rather than inferred: BpmnParse.checkActivityInputOutputSupported
   * accepts a tag containing "task" or "Event" plus subProcess, transaction and callActivity, and
   * rejects the rest at deploy time. Gateways and event sub-processes are the two cases people
   * expect to work.
   */
  @Test
  void allowsInputOutputMappingOnExactlyTheElementsTheParserAccepts() {
    List<String> accepted = catalogue.all().stream()
        .filter(shape -> catalogue.effectiveProperties(shape).stream()
            .anyMatch(property -> property.name().equals("camunda:inputOutput")))
        .map(Shape::id).toList();

    assertThat(accepted).containsExactlyInAnyOrder(
        "task", "user-task", "service-task", "send-task", "receive-task", "manual-task",
        "script-task", "business-rule-task",
        "sub-process", "transaction", "call-activity",
        "start-event", "intermediate-catch-event", "intermediate-throw-event",
        "boundary-event", "end-event");

    assertThat(accepted)
        .as("gateways and event sub-processes are rejected by the parser")
        .doesNotContain("exclusive-gateway", "parallel-gateway", "inclusive-gateway",
            "event-based-gateway", "complex-gateway", "event-sub-process", "sequence-flow");
  }

  /** The two elements whose restriction is on output parameters only, not on the mapping itself. */
  @Test
  void recordsTheOutputParameterRestrictions() {
    assertThat(catalogue.shape("end-event").constraints())
        .anySatisfy(constraint -> assertThat(constraint).contains("camunda:outputParameter"));
    assertThat(catalogue.group(PropertyGroups.ACTIVITY).properties())
        .filteredOn(property -> property.name().equals("bpmn:multiInstanceLoopCharacteristics"))
        .singleElement()
        .satisfies(property -> assertThat(property.description()).contains("camunda:outputParameter"));
    assertThat(catalogue.shape("event-sub-process").constraints())
        .anySatisfy(constraint -> assertThat(constraint).contains("camunda:inputOutput"));
  }

  /** The label is what a person reads; the XML name is what they type. Both, always. */
  @Test
  void givesEveryPropertyAReadableLabelAlongsideItsXmlName() {
    assertThat(catalogue.all()).allSatisfy(shape ->
        assertThat(catalogue.effectiveProperties(shape)).allSatisfy(property -> {
          assertThat(property.label()).as(shape.id() + "." + property.name()).isNotBlank();
          assertThat(property.label().charAt(0)).as(property.name() + " label starts upper")
              .isUpperCase();
          assertThat(property.label()).doesNotContain(":");
        }));
  }

  @Test
  void derivesLabelsAndPrefersCamundasOwnWording() {
    // Derived mechanically.
    assertThat(PropertyLabels.labelFor("camunda:candidateGroups")).isEqualTo("Candidate groups");
    assertThat(PropertyLabels.labelFor("camunda:calledElementVersion")).isEqualTo("Called element version");
    // Overridden, because the derived form reads badly.
    assertThat(PropertyLabels.labelFor("id")).isEqualTo("ID");
    assertThat(PropertyLabels.labelFor("isExecutable")).isEqualTo("Executable");
    assertThat(PropertyLabels.labelFor("attachedToRef")).isEqualTo("Attached to");
    // Overridden, because Camunda already has a word for it.
    assertThat(PropertyLabels.labelFor("camunda:asyncBefore")).isEqualTo("Asynchronous before");
    assertThat(PropertyLabels.labelFor("camunda:failedJobRetryTimeCycle")).isEqualTo("Retry time cycle");
  }

  @Test
  void showsAConcreteValueForThePropertiesWhereOneHelps() {
    assertThat(service("user-task", "camunda:assignee").example()).isEqualTo("${initiator}");
    assertThat(service("service-task", "camunda:failedJobRetryTimeCycle").example()).isEqualTo("R3/PT10M");
    assertThat(service("boundary-event", "attachedToRef").example()).isNotBlank();
    // An enum needs no example - its allowedValues already are one.
    assertThat(service("call-activity", "camunda:calledElementBinding").allowedValues()).isNotEmpty();
  }

  private Property service(String shapeId, String propertyName) {
    return catalogue.effectiveProperties(catalogue.shape(shapeId)).stream()
        .filter(property -> property.name().equals(propertyName)).findFirst().orElseThrow();
  }

  @Test
  void explainsTheVocabularyWithoutJargonAndWithAnExample() {
    List<Concept> concepts = catalogue.concepts();

    assertThat(concepts).extracting(Concept::id).contains("shape", "property", "label-and-tag",
        "namespace", "property-group", "executable", "notation", "constraint");
    assertThat(concepts).allSatisfy(concept -> {
      assertThat(concept.inShort()).as(concept.id() + ".inShort").isNotBlank();
      assertThat(concept.explanation()).as(concept.id() + ".explanation").isNotBlank();
      assertThat(concept.example()).as(concept.id() + ".example").isNotBlank();
    });
    // Every "related" pointer must resolve, or the reader follows it into a 404.
    List<String> ids = concepts.stream().map(Concept::id).toList();
    assertThat(concepts).allSatisfy(concept ->
        assertThat(ids).as(concept.id() + ".related").containsAll(concept.related()));
  }

  /**
   * Two beliefs about collapsed sub-processes that the catalogue has to answer, because both are
   * half-true and the half that is wrong costs a deployment.
   */
  @Test
  void separatesCollapseTheDiagramFlagFromCollapseTheToolingLimitation() {
    Shape subProcess = catalogue.shape("sub-process");

    // isExpanded is diagram interchange, not behaviour: the engine records it and moves on.
    Property expanded = subProcess.properties().stream()
        .filter(property -> property.name().equals("isExpanded")).findFirst().orElseThrow();
    assertThat(expanded.kind()).isEqualTo(Property.DI_ATTRIBUTE);
    assertThat(expanded.description()).contains("nothing branches on it");

    // The real cause of "a collapsed sub-process does not work" is that it is empty.
    assertThat(subProcess.constraints())
        .anySatisfy(constraint -> assertThat(constraint).contains("must define a startEvent"));
    // And the asymmetry is in the modeller, not in BPMN.
    assertThat(subProcess.constraints())
        .anySatisfy(constraint -> assertThat(constraint).contains("bpmn-js-collapse-subprocess"));
  }

  @Test
  void marksTheShapesTheEngineIgnores() {
    List<String> ignored = catalogue.all().stream()
        .filter(shape -> !shape.executable()).map(Shape::id).toList();

    // The four that most often surprise people: drawn by every modeller, executed by none.
    assertThat(ignored).contains("complex-gateway", "ad-hoc-sub-process",
        "data-object-reference", "group");
  }

  @Test
  void givesEveryDrawnShapeBoundsAndEveryEdgeNone() {
    assertThat(catalogue.all()).allSatisfy(shape -> {
      if ("bpmndi:BPMNShape".equals(shape.notation().diElement())) {
        assertThat(shape.notation().defaultWidth()).as(shape.id() + " width").isNotNull().isPositive();
        assertThat(shape.notation().defaultHeight()).as(shape.id() + " height").isNotNull().isPositive();
      } else {
        assertThat(shape.notation().defaultWidth()).as(shape.id() + " width").isNull();
      }
    });
  }

  @Test
  void describesEveryPropertyItDeclares() {
    assertThat(catalogue.all()).allSatisfy(shape ->
        assertThat(shape.properties()).allSatisfy(property -> {
          assertThat(property.description()).as(shape.id() + "." + property.name()).isNotBlank();
          assertThat(property.namespace()).isIn(Property.BPMN, Property.CAMUNDA, Property.BPMNDI);
          assertThat(property.kind()).isIn(Property.ATTRIBUTE, Property.CHILD_ELEMENT,
              Property.EXTENSION_ELEMENT, Property.DI_ATTRIBUTE);
        }));
  }

  /** An enum property whose default is not one of its own values would be a silent lie. */
  @Test
  void keepsEveryDefaultWithinItsAllowedValues() {
    catalogue.all().forEach(shape -> shape.properties().stream()
        .filter(property -> !property.allowedValues().isEmpty() && property.defaultValue() != null)
        .forEach(property -> assertThat(property.allowedValues())
            .as(shape.id() + "." + property.name() + " default")
            .contains(property.defaultValue())));
  }

  @Test
  void derivesNamespaceFromThePrefix() {
    assertThat(Property.attr("camunda:assignee", Property.STRING, "d").namespace())
        .isEqualTo(Property.CAMUNDA);
    assertThat(Property.attr("isExecutable", Property.BOOLEAN, "d").namespace())
        .isEqualTo(Property.BPMN);
    assertThat(Property.di("isExpanded", Property.BOOLEAN, "d").namespace())
        .isEqualTo(Property.BPMNDI);
  }

  @Test
  void refusesAnUnknownShapeIdWithAPointerToTheListing() {
    assertThatThrownBy(() -> catalogue.shape("user-tsak"))
        .isInstanceOf(UnknownElementException.class)
        .hasMessageContaining("user-tsak")
        .hasMessageContaining("/api/v1/shapes");
  }

  @Test
  void refusesAnUnknownPropertyGroup() {
    assertThatThrownBy(() -> catalogue.group("no-such-group"))
        .isInstanceOf(UnknownElementException.class)
        .hasMessageContaining("no-such-group");
  }
}
