package com.argus.properties.catalogue.model;

import java.util.List;

/**
 * How the shape is drawn, and the geometry a modeller creates it at.
 *
 * <p>Kept separate from {@link Property} because notation is not something you configure - it is
 * a consequence of the element type. A caller generating BPMN DI needs the default bounds; a
 * caller rendering a palette needs the description. Neither belongs in the property list.
 *
 * @param diElement    {@code bpmndi:BPMNShape}, {@code bpmndi:BPMNEdge}, or null for elements
 *                     that have no visual representation at all
 * @param defaultWidth width Camunda Modeler / bpmn-js creates the shape at; null for edges
 * @param markers      glyphs that can appear on the shape, and what turns each one on
 */
public record Notation(String diElement,
                       Integer defaultWidth,
                       Integer defaultHeight,
                       boolean resizable,
                       String render,
                       List<String> markers) {

  public static final String SHAPE = "bpmndi:BPMNShape";
  public static final String EDGE = "bpmndi:BPMNEdge";

  public Notation {
    markers = markers == null ? List.of() : List.copyOf(markers);
  }

  public static Notation shape(int width, int height, boolean resizable, String render) {
    return new Notation(SHAPE, width, height, resizable, render, List.of());
  }

  public static Notation shape(int width, int height, boolean resizable, String render, List<String> markers) {
    return new Notation(SHAPE, width, height, resizable, render, markers);
  }

  public static Notation edge(String render) {
    return new Notation(EDGE, null, null, false, render, List.of());
  }

  /** For elements that are real BPMN but never drawn - {@code bpmn:dataObject}, {@code bpmn:message}. */
  public static Notation none(String why) {
    return new Notation(null, null, null, false, why, List.of());
  }
}
