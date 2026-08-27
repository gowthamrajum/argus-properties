package com.argus.properties.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.properties.catalogue.model.Property;
import com.argus.properties.catalogue.model.PropertiesResponse;
import com.argus.properties.catalogue.model.PropertyUsage;
import com.argus.properties.catalogue.model.Shape;
import com.argus.properties.catalogue.model.ShapeSummary;
import com.argus.properties.catalogue.model.ShapesResponse;
import com.argus.properties.exception.UnknownElementException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShapeServiceTest {

  private final ShapeService service = new ShapeService(new ShapeCatalogue());

  @Test
  void listsEveryShapeWithCountsThatAgreeWithTheList() {
    ShapesResponse response = service.shapes(null, null, null);

    assertThat(response.shapes()).hasSize(response.shapeCount());
    assertThat(response.countsByCategory().values().stream().mapToInt(Integer::intValue).sum())
        .isEqualTo(response.shapeCount());
  }

  @Test
  void filtersByCategoryCaseInsensitively() {
    ShapesResponse response = service.shapes("gateway", null, null);

    assertThat(response.shapes()).isNotEmpty()
        .allSatisfy(shape -> assertThat(shape.category()).isEqualTo(Shape.GATEWAY));
    assertThat(response.countsByCategory()).containsOnlyKeys(Shape.GATEWAY);
  }

  @Test
  void searchesTagsAndSummariesNotJustNames() {
    // "bpmn:userTask" appears in the tag, never in the display name.
    assertThat(service.shapes(null, "bpmn:userTask", null).shapes())
        .extracting(ShapeSummary::id).containsExactly("user-task");
    assertThat(service.shapes(null, "boundary", null).shapes())
        .extracting(ShapeSummary::id).contains("boundary-event");
  }

  @Test
  void separatesExecutableShapesFromModellingOnlyOnes() {
    assertThat(service.shapes(null, null, false).shapes())
        .extracting(ShapeSummary::id).contains("complex-gateway", "group");
    assertThat(service.shapes(null, null, true).shapes())
        .extracting(ShapeSummary::id).contains("user-task").doesNotContain("complex-gateway");
  }

  @Test
  void returnsInheritedAndOwnPropertiesByDefault() {
    PropertiesResponse response = service.properties("service-task", false, null);

    assertThat(response.properties()).extracting(Property::name)
        .contains("id", "name", "camunda:asyncBefore", "camunda:delegateExpression", "implementation");
    assertThat(response.propertyCount()).isEqualTo(response.properties().size());
  }

  @Test
  void ownDropsEverythingInherited() {
    PropertiesResponse response = service.properties("service-task", true, null);

    assertThat(response.properties()).allSatisfy(property ->
        assertThat(property.inheritedFrom()).isNull());
    assertThat(response.properties()).extracting(Property::name)
        .contains("implementation").doesNotContain("id", "camunda:asyncBefore");
  }

  @Test
  void narrowsToTheVendorExtensions() {
    PropertiesResponse response = service.properties("user-task", false, "camunda");

    assertThat(response.properties()).isNotEmpty().allSatisfy(property ->
        assertThat(property.namespace()).isEqualTo(Property.CAMUNDA));
    assertThat(response.countsByNamespace()).containsOnlyKeys(Property.CAMUNDA);
  }

  @Test
  void findsOnePropertyByItsPrefixedName() {
    Property assignee = service.property("user-task", "camunda:assignee");

    assertThat(assignee.kind()).isEqualTo(Property.ATTRIBUTE);
    assertThat(assignee.namespace()).isEqualTo(Property.CAMUNDA);
    assertThat(assignee.description()).contains("claim");
  }

  @Test
  void findsAnInheritedPropertyThroughTheShapeThatInheritsIt() {
    Property async = service.property("service-task", "camunda:asyncBefore");

    assertThat(async.inheritedFrom()).isEqualTo(PropertyGroups.CAMUNDA_ASYNC);
    assertThat(async.defaultValue()).isEqualTo("false");
  }

  @Test
  void refusesAnUnknownCategoryRatherThanReturningNothing() {
    // An empty list would read as "there are no gateways", which is a different answer.
    assertThatThrownBy(() -> service.shapes("GATEWAYS", null, null))
        .isInstanceOf(UnknownElementException.class)
        .hasMessageContaining("/api/v1/categories");
  }

  @Test
  void refusesAnUnknownPropertyWithAPointerToTheListing() {
    assertThatThrownBy(() -> service.property("user-task", "camunda:asignee"))
        .isInstanceOf(UnknownElementException.class)
        .hasMessageContaining("/api/v1/shapes/user-task/properties");
  }

  /**
   * The question the shape-indexed catalogue cannot answer: which shapes support this property?
   * Listeners are the case that prompted it - they are inherited, so they are invisible in a
   * shape's own declarations and easy to conclude are missing.
   */
  @Test
  void indexesEveryPropertyByTheShapesItAppliesOn() {
    PropertyUsage listeners = service.propertyUsage("camunda:executionListener");

    assertThat(listeners.label()).isEqualTo("Execution listeners");
    assertThat(listeners.kind()).isEqualTo(Property.EXTENSION_ELEMENT);
    assertThat(listeners.shapeCount()).isEqualTo(23);
    assertThat(listeners.occurrences()).extracting(PropertyUsage.Occurrence::shapeId)
        .contains("user-task", "exclusive-gateway", "start-event", "process", "sequence-flow");

    // Declared directly on those two, inherited everywhere else.
    assertThat(listeners.occurrences())
        .filteredOn(o -> o.shapeId().equals("process") || o.shapeId().equals("sequence-flow"))
        .allSatisfy(o -> assertThat(o.inheritedFrom()).isNull());
    assertThat(listeners.occurrences()).filteredOn(o -> o.shapeId().equals("user-task"))
        .singleElement().extracting(PropertyUsage.Occurrence::inheritedFrom)
        .isEqualTo(PropertyGroups.CAMUNDA_EXTENSIONS);
  }

  @Test
  void keepsThePerShapeDescriptionBecauseTheMeaningShifts() {
    PropertyUsage listeners = service.propertyUsage("camunda:executionListener");

    // A sequence flow's listener fires on take; a flow node's on start or end. One sentence for
    // both would lose the only part worth reading.
    assertThat(descriptionOn(listeners, "sequence-flow")).contains("take");
    assertThat(descriptionOn(listeners, "user-task")).contains("start");
  }

  @Test
  void showsTaskListenersOnTheOneShapeThatHasThem() {
    PropertyUsage taskListeners = service.propertyUsage("camunda:taskListener");

    assertThat(taskListeners.label()).isEqualTo("Task listeners");
    assertThat(taskListeners.shapeCount()).isEqualTo(1);
    assertThat(taskListeners.occurrences()).singleElement()
        .satisfies(o -> {
          assertThat(o.shapeId()).isEqualTo("user-task");
          assertThat(o.inheritedFrom()).as("declared on the shape, not inherited").isNull();
        });
  }

  @Test
  void narrowsTheIndexToTheExtensionElements() {
    List<PropertyUsage> extensions = service.propertyIndex(Property.EXTENSION_ELEMENT, null, null);

    assertThat(extensions).isNotEmpty()
        .allSatisfy(usage -> assertThat(usage.kind()).isEqualTo(Property.EXTENSION_ELEMENT));
    assertThat(extensions).extracting(PropertyUsage::name)
        .contains("camunda:executionListener", "camunda:taskListener", "camunda:inputOutput");
    // The listing is a summary: occurrences would make it an order of magnitude larger.
    assertThat(extensions).allSatisfy(usage -> assertThat(usage.occurrences()).isEmpty());
  }

  @Test
  void searchesTheIndexByLabelAsWellAsXmlName() {
    assertThat(service.propertyIndex(null, null, "listener")).extracting(PropertyUsage::name)
        .contains("camunda:executionListener", "camunda:taskListener");
    // "Retry time cycle" is findable by its label; the XML name contains no such word.
    assertThat(service.propertyIndex(null, null, "retry")).extracting(PropertyUsage::name)
        .contains("camunda:failedJobRetryTimeCycle");
  }

  @Test
  void refusesAPropertyNoShapeDeclares() {
    assertThatThrownBy(() -> service.propertyUsage("camunda:nonsense"))
        .isInstanceOf(UnknownElementException.class)
        .hasMessageContaining("/api/v1/properties");
  }

  private static String descriptionOn(PropertyUsage usage, String shapeId) {
    return usage.occurrences().stream().filter(o -> o.shapeId().equals(shapeId))
        .findFirst().orElseThrow().description();
  }

  @Test
  void groupsCategoriesWithTheirShapeIds() {
    assertThat(service.categories()).isNotEmpty().allSatisfy(entry ->
        assertThat(entry.shapeIds()).hasSize(entry.shapeCount()));
  }
}
