package com.argus.properties.catalogue.model;

import java.util.List;

/**
 * One thing you can set on a BPMN shape.
 *
 * <p>"Property" is deliberately wider than "XML attribute". A modeller's property panel does not
 * distinguish between {@code camunda:assignee} (an attribute), {@code bpmn:conditionExpression}
 * (a child element), {@code camunda:executionListener} (an extension element) and
 * {@code isExpanded} (a DI attribute) - all four are things you set on the shape. Flattening them
 * into one type is what lets {@code /shapes/{id}/properties} answer "what can I configure here?"
 * in a single list. {@link #kind} preserves where the value actually lands in the XML, which is
 * what a serialiser needs.
 *
 * @param name          the XML name including its prefix, e.g. {@code camunda:assignee} - what
 *                      you type into the file
 * @param label         the human-readable name, e.g. "Assignee". Derived from the XML name, or
 *                      taken from Camunda's own vocabulary where it has one. See
 *                      {@link PropertyLabels}.
 * @param kind          where the value lives: attribute, child element, extension element, or DI
 * @param namespace     derived from the prefix; the axis a UI groups by (BPMN vs Camunda tab)
 * @param example       a concrete value you could type, or null where an enum's allowedValues
 *                      already serve as one
 * @param defaultValue  the value the engine assumes when the property is absent, or null
 * @param inheritedFrom id of the {@link PropertyGroup} this came from, or null when the shape
 *                      declares it itself - so a caller can tell "specific to a user task" from
 *                      "true of every flow node"
 */
public record Property(String name,
                       String label,
                       String kind,
                       String namespace,
                       String type,
                       boolean required,
                       String example,
                       String defaultValue,
                       List<String> allowedValues,
                       String description,
                       String inheritedFrom) {

  // Kinds - where the value lands in the XML.
  public static final String ATTRIBUTE = "ATTRIBUTE";
  public static final String CHILD_ELEMENT = "CHILD_ELEMENT";
  public static final String EXTENSION_ELEMENT = "EXTENSION_ELEMENT";
  public static final String DI_ATTRIBUTE = "DI_ATTRIBUTE";

  // Namespaces.
  public static final String BPMN = "bpmn";
  public static final String CAMUNDA = "camunda";
  public static final String BPMNDI = "bpmndi";

  // Types. Coarse on purpose: the useful distinction to a caller is "is this a literal, an
  // expression, or a reference to another element", not the XSD simple type.
  public static final String STRING = "string";
  public static final String BOOLEAN = "boolean";
  public static final String INTEGER = "int";
  public static final String EXPRESSION = "expression";
  public static final String IDREF = "idref";
  public static final String IDREFS = "idrefs";
  public static final String ENUM = "enum";
  public static final String ELEMENT = "element";

  public Property {
    allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
  }

  public static Property attr(String name, String type, String description) {
    return of(name, ATTRIBUTE, namespaceOf(name), type, false, null, null, description);
  }

  public static Property attr(String name, String type, String defaultValue, String description) {
    return of(name, ATTRIBUTE, namespaceOf(name), type, false, defaultValue, null, description);
  }

  public static Property requiredAttr(String name, String type, String description) {
    return of(name, ATTRIBUTE, namespaceOf(name), type, true, null, null, description);
  }

  /** An attribute with a closed set of legal values; {@code defaultValue} may be null. */
  public static Property choice(String name, String defaultValue, List<String> values, String description) {
    return of(name, ATTRIBUTE, namespaceOf(name), ENUM, false, defaultValue, values, description);
  }

  public static Property child(String name, String description) {
    return of(name, CHILD_ELEMENT, namespaceOf(name), ELEMENT, false, null, null, description);
  }

  public static Property requiredChild(String name, String description) {
    return of(name, CHILD_ELEMENT, namespaceOf(name), ELEMENT, true, null, null, description);
  }

  /** A {@code camunda:*} element that lives inside {@code <bpmn:extensionElements>}. */
  public static Property ext(String name, String description) {
    return of(name, EXTENSION_ELEMENT, namespaceOf(name), ELEMENT, false, null, null, description);
  }

  /** An attribute of the shape's {@code bpmndi:BPMNShape}, not of the semantic element. */
  public static Property di(String name, String type, String description) {
    return of(name, DI_ATTRIBUTE, BPMNDI, type, false, null, null, description);
  }

  /** Single funnel, so the label is derived in exactly one place. */
  private static Property of(String name, String kind, String namespace, String type,
                             boolean required, String defaultValue, List<String> values,
                             String description) {
    return new Property(name, PropertyLabels.labelFor(name), kind, namespace, type, required,
        PropertyExamples.exampleFor(name), defaultValue, values, description, null);
  }

  /**
   * Stamps this property with the group it came from. Arity distinguishes it from the
   * {@code inheritedFrom()} accessor.
   */
  public Property declaredIn(String groupId) {
    return new Property(name, label, kind, namespace, type, required, example, defaultValue,
        allowedValues, description, groupId);
  }

  private static String namespaceOf(String name) {
    if (name.startsWith("camunda:")) {
      return CAMUNDA;
    }
    return name.startsWith("bpmndi:") || name.startsWith("dc:") || name.startsWith("di:")
        ? BPMNDI
        : BPMN;
  }
}
