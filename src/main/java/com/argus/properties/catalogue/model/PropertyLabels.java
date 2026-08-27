package com.argus.properties.catalogue.model;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Human-readable names for XML property names.
 *
 * <p>The XML name is precise and unreadable: {@code camunda:isStartableInTasklist} is not a phrase
 * anyone says out loud, and a table of a hundred of them is a wall of camelCase. But it is also
 * the thing you type into the file, so it cannot be replaced - only accompanied. Every property
 * therefore carries both: a label to read and the tag to write.
 *
 * <p>Most labels derive mechanically from the name, so a new property gets a sensible one for
 * free. {@link #OVERRIDES} covers the cases where that reads badly ({@code id} becoming "Id",
 * {@code isExecutable} becoming "Is executable") and, more importantly, the cases where Camunda's
 * own Modeler and documentation already have a word for it. Where they do, that word wins - a
 * catalogue that invents its own vocabulary for a tool people already use is worse than no
 * catalogue.
 */
public final class PropertyLabels {

  private static final Map<String, String> OVERRIDES = Map.ofEntries(
      // ---- reads badly when derived -------------------------------------------------
      Map.entry("id", "ID"),
      Map.entry("isExecutable", "Executable"),
      Map.entry("isCollection", "Collection"),
      Map.entry("isClosed", "Closed"),
      Map.entry("isExpanded", "Expanded"),
      Map.entry("isForCompensation", "For compensation"),
      Map.entry("isHorizontal", "Horizontal"),
      Map.entry("isImmediate", "Immediate"),
      Map.entry("isInterrupting", "Interrupting"),
      Map.entry("isMarkerVisible", "Marker visible"),
      Map.entry("isUnlimited", "Unlimited"),
      Map.entry("camunda:isStartableInTasklist", "Startable in Tasklist"),

      // ---- *Ref attributes are references; the label says what to, not that it is one -
      Map.entry("attachedToRef", "Attached to"),
      Map.entry("sourceRef", "Source"),
      Map.entry("targetRef", "Target"),
      Map.entry("bpmn:sourceRef", "Source"),
      Map.entry("bpmn:targetRef", "Target"),
      Map.entry("messageRef", "Message"),
      Map.entry("errorRef", "Error"),
      Map.entry("signalRef", "Signal"),
      Map.entry("escalationRef", "Escalation"),
      Map.entry("activityRef", "Activity to compensate"),
      Map.entry("processRef", "Process"),
      Map.entry("dataObjectRef", "Data object"),
      Map.entry("dataStoreRef", "Data store"),
      Map.entry("categoryValueRef", "Category value"),
      Map.entry("itemSubjectRef", "Item definition"),
      Map.entry("partitionElementRef", "Partition element"),
      Map.entry("operationRef", "Operation"),
      Map.entry("bpmn:eventDefinitionRef", "Event definition reference"),
      Map.entry("default", "Default flow"),

      // ---- the words Camunda Modeler and the Camunda docs actually use ---------------
      Map.entry("camunda:asyncBefore", "Asynchronous before"),
      Map.entry("camunda:asyncAfter", "Asynchronous after"),
      Map.entry("camunda:async", "Asynchronous"),
      Map.entry("camunda:failedJobRetryTimeCycle", "Retry time cycle"),
      Map.entry("camunda:executionListener", "Execution listeners"),
      Map.entry("camunda:taskListener", "Task listeners"),
      Map.entry("camunda:inputOutput", "Input/output mapping"),
      Map.entry("camunda:properties", "Extension properties"),
      Map.entry("camunda:formData", "Generated form fields"),
      Map.entry("camunda:field", "Field injections"),
      Map.entry("camunda:errorEventDefinition", "Error event mapping"),
      Map.entry("camunda:class", "Java class"),
      Map.entry("camunda:type", "Implementation type"),
      Map.entry("camunda:resource", "Script resource"),
      Map.entry("camunda:in", "In mappings"),
      Map.entry("camunda:out", "Out mappings"),
      Map.entry("camunda:followUpDate", "Follow-up date"),
      Map.entry("camunda:formRef", "Form reference"),
      Map.entry("camunda:formRefBinding", "Form binding"),
      Map.entry("camunda:formRefVersion", "Form version"),
      Map.entry("camunda:decisionRef", "Decision reference"),
      Map.entry("camunda:decisionRefBinding", "Decision binding"),
      Map.entry("camunda:decisionRefVersion", "Decision version"),
      Map.entry("camunda:decisionRefVersionTag", "Decision version tag"),
      Map.entry("camunda:decisionRefTenantId", "Decision tenant ID"),
      Map.entry("camunda:mapDecisionResult", "Decision result mapping"),
      Map.entry("camunda:caseRef", "Case reference"),
      Map.entry("camunda:caseTenantId", "Case tenant ID"),
      Map.entry("camunda:calledElementTenantId", "Called element tenant ID"),

      // ---- child elements: plural where the element repeats --------------------------
      Map.entry("bpmn:incoming", "Incoming flows"),
      Map.entry("bpmn:outgoing", "Outgoing flows"),
      Map.entry("bpmn:flowNodeRef", "Flow nodes in this lane"),
      Map.entry("bpmn:lane", "Lanes"),
      Map.entry("bpmn:childLaneSet", "Nested lanes"),
      Map.entry("bpmn:participant", "Participants"),
      Map.entry("bpmn:messageFlow", "Message flows"),
      Map.entry("bpmn:multiInstanceLoopCharacteristics", "Multi-instance"),
      Map.entry("bpmn:standardLoopCharacteristics", "Loop"),
      Map.entry("bpmn:ioSpecification", "I/O specification"),
      Map.entry("bpmn:property", "Element property"),
      Map.entry("bpmn:timeDate", "Time date"),
      Map.entry("bpmn:timeDuration", "Time duration"),
      Map.entry("bpmn:timeCycle", "Time cycle"));

  private PropertyLabels() {
  }

  /** Every overridden name, so the catalogue can fail startup on one nothing uses any more. */
  public static Set<String> overriddenNames() {
    return OVERRIDES.keySet();
  }

  public static String labelFor(String name) {
    String override = OVERRIDES.get(name);
    return override != null ? override : derive(name);
  }

  /**
   * {@code camunda:candidateGroups} to "Candidate groups": drop the prefix, split the camel hump,
   * then sentence-case. Acronyms are left intact so {@code tenantId} does not become "Tenant i d".
   */
  private static String derive(String name) {
    String local = name.substring(name.indexOf(':') + 1);
    String spaced = local
        .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");

    String[] words = spaced.split(" ");
    StringBuilder label = new StringBuilder();
    for (int i = 0; i < words.length; i++) {
      String word = words[i];
      if (word.isEmpty()) {
        continue;
      }
      if (!label.isEmpty()) {
        label.append(' ');
      }
      // Only the first word is capitalised - "Called element binding", not Title Case.
      label.append(i == 0
          ? Character.toUpperCase(word.charAt(0)) + word.substring(1)
          : word.toLowerCase(Locale.ROOT));
    }
    return label.toString();
  }
}
