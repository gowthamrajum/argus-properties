package com.argus.properties.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argus.properties.catalogue.model.Shape;
import com.argus.properties.exception.UnknownElementException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import org.finos.fluxnova.bpm.model.bpmn.Bpmn;
import org.finos.fluxnova.bpm.model.bpmn.BpmnModelInstance;
import org.finos.fluxnova.bpm.model.bpmn.instance.BaseElement;
import org.finos.fluxnova.bpm.model.bpmn.instance.bpmndi.BpmnDiagram;
import org.finos.fluxnova.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.Test;

/**
 * The previews are only worth anything if they are real BPMN, so they are checked against a real
 * BPMN parser rather than against a string.
 *
 * <p>This is the one place the project parses BPMN, and it is test scope on purpose: the service
 * describes what a .bpmn file may contain and never reads one. Generating files it cannot itself
 * validate would be the sort of claim that quietly stops being true.
 */
class BpmnPreviewTest {

  private final ShapeCatalogue catalogue = new ShapeCatalogue();
  private final BpmnPreview preview = new BpmnPreview();

  @Test
  void everyDrawableShapeProducesAFileTheBpmnParserAccepts() {
    List<Shape> drawable = catalogue.all().stream().filter(preview::canPreview).toList();

    assertThat(drawable).hasSize(46);
    assertThat(drawable).allSatisfy(shape -> {
      BpmnModelInstance model = parse(preview.xmlFor(shape));
      Collection<ModelElementInstance> diagrams =
          model.getModelElementsByType(model.getModel().getType(BpmnDiagram.class));

      assertThat(diagrams.isEmpty())
          .as(shape.id() + " must carry diagram interchange, or it opens as a blank canvas")
          .isFalse();
      assertThat((Object) model.getModelElementById("Element_1"))
          .as(shape.id() + " must contain the element the preview is of")
          .isNotNull();
    });
  }

  /** The preview must be of the shape asked for, not something that merely looks like it. */
  @Test
  void putsTheRequestedElementInTheDocument() {
    assertThat(elementTagOf("user-task")).isEqualTo("userTask");
    assertThat(elementTagOf("exclusive-gateway")).isEqualTo("exclusiveGateway");
    assertThat(elementTagOf("call-activity")).isEqualTo("callActivity");
    assertThat(elementTagOf("sequence-flow")).isEqualTo("sequenceFlow");
    assertThat(elementTagOf("participant")).isEqualTo("participant");
    assertThat(elementTagOf("text-annotation")).isEqualTo("textAnnotation");
  }

  /**
   * An event definition is an icon inside a circle, never a shape of its own, so the preview shows
   * it on a host event - and the definition really has to be in there.
   */
  @Test
  void drawsAnEventDefinitionInsideAHostEvent() {
    String xml = preview.xmlFor(catalogue.shape("timer-event-definition"));

    assertThat(xml).contains("<bpmn:intermediateCatchEvent").contains("<bpmn:timerEventDefinition");
    assertThat((Object) parse(xml).getModelElementById("Definition_1")).isNotNull();
  }

  @Test
  void suppliesTheContextShapesThatCannotStandAlone() {
    // A boundary event needs something to be attached to.
    assertThat(preview.xmlFor(catalogue.shape("boundary-event")))
        .contains("attachedToRef=\"Host_1\"").contains("<bpmn:task id=\"Host_1\"");
    // A message flow needs two participants to cross between.
    assertThat(preview.xmlFor(catalogue.shape("message-flow")))
        .contains("<bpmn:collaboration").contains("Pool_1").contains("Pool_2");
    // A lane needs a pool to sit inside.
    assertThat(preview.xmlFor(catalogue.shape("lane")))
        .contains("<bpmn:laneSet").contains("processRef=\"Preview_Process\"");
  }

  /** Geometry comes from the catalogue, so the picture cannot drift from the documented bounds. */
  @Test
  void laysShapesOutAtTheirOwnDocumentedBounds() {
    assertThat(preview.xmlFor(catalogue.shape("start-event"))).contains("width=\"36\" height=\"36\"");
    assertThat(preview.xmlFor(catalogue.shape("exclusive-gateway"))).contains("width=\"50\" height=\"50\"");
    assertThat(preview.xmlFor(catalogue.shape("user-task"))).contains("width=\"100\" height=\"80\"");
    assertThat(preview.xmlFor(catalogue.shape("data-object-reference"))).contains("width=\"36\" height=\"50\"");
  }

  /** Without isMarkerVisible an exclusive gateway renders as a bare diamond - the wrong shape. */
  @Test
  void keepsTheDiagramOnlyFlagsThatChangeWhatIsDrawn() {
    assertThat(preview.xmlFor(catalogue.shape("exclusive-gateway"))).contains("isMarkerVisible=\"true\"");
    assertThat(preview.xmlFor(catalogue.shape("parallel-gateway"))).doesNotContain("isMarkerVisible");
    assertThat(preview.xmlFor(catalogue.shape("sub-process"))).contains("isExpanded=\"false\"");
    assertThat(preview.xmlFor(catalogue.shape("event-sub-process"))).contains("triggeredByEvent=\"true\"");
  }

  @Test
  void refusesTheShapesThatAreNeverDrawnAndSaysWhy() {
    List<String> notDrawn = catalogue.all().stream()
        .filter(shape -> !preview.canPreview(shape)).map(Shape::id).toList();

    assertThat(notDrawn).containsExactlyInAnyOrder(
        "process", "collaboration", "lane-set", "data-object", "data-store");

    assertThatThrownBy(() -> preview.xmlFor(catalogue.shape("data-object")))
        .isInstanceOf(UnknownElementException.class)
        .hasMessageContaining("never drawn");
  }

  private String elementTagOf(String shapeId) {
    BaseElement element = (BaseElement) parse(preview.xmlFor(catalogue.shape(shapeId)))
        .getModelElementById("Element_1");
    return element.getElementType().getTypeName();
  }

  private static BpmnModelInstance parse(String xml) {
    return Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }
}
