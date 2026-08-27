package com.argus.properties.catalogue.model;

import java.util.Map;
import java.util.Set;

/**
 * A concrete value for a property, so the description does not have to carry one.
 *
 * <p>"ISO-8601 repeating interval controlling retries" is accurate and tells you nothing about
 * what to type. {@code R3/PT10M} tells you everything. Most people read the example first and the
 * description only if the example surprises them, so both are served and the example comes first
 * in the UI.
 *
 * <p>Kept in a map rather than on each declaration for the same reason as {@link PropertyLabels}:
 * the same property means the same thing wherever it appears, and one table is far easier to keep
 * honest than the same literal repeated across eight catalogue files.
 */
public final class PropertyExamples {

  private static final Map<String, String> EXAMPLES = Map.ofEntries(
      // ---- identity and naming -------------------------------------------------------
      Map.entry("id", "Task_ApproveOrder"),
      Map.entry("name", "Approve order"),
      Map.entry("bpmn:documentation", "Approver has two working days to respond."),

      // ---- process ---------------------------------------------------------------------
      Map.entry("isExecutable", "true"),
      Map.entry("camunda:historyTimeToLive", "P180D"),
      Map.entry("camunda:versionTag", "2.1"),
      Map.entry("camunda:candidateStarterGroups", "sales,support"),
      Map.entry("camunda:isStartableInTasklist", "false"),

      // ---- user task -------------------------------------------------------------------
      Map.entry("camunda:assignee", "${initiator}"),
      Map.entry("camunda:candidateGroups", "accounting,management"),
      Map.entry("camunda:candidateUsers", "mary,john"),
      Map.entry("camunda:dueDate", "${dateVariable}"),
      Map.entry("camunda:followUpDate", "${dateVariable}"),
      Map.entry("camunda:priority", "50"),
      Map.entry("camunda:formKey", "camunda-forms:deployment:approve.form"),
      Map.entry("camunda:formRef", "approveOrder"),
      Map.entry("camunda:taskListener",
          "<camunda:taskListener event=\"create\" class=\"com.acme.NotifyApprover\" />"),
      Map.entry("camunda:formData",
          "<camunda:formField id=\"amount\" label=\"Amount\" type=\"long\" />"),

      // ---- implementation --------------------------------------------------------------
      Map.entry("camunda:class", "com.acme.payments.ChargeCardDelegate"),
      Map.entry("camunda:delegateExpression", "${chargeCardDelegate}"),
      Map.entry("camunda:expression", "${payments.charge(execution)}"),
      Map.entry("camunda:resultVariable", "chargeResult"),
      Map.entry("camunda:type", "external"),
      Map.entry("camunda:topic", "payment"),
      Map.entry("camunda:taskPriority", "50"),
      Map.entry("camunda:field",
          "<camunda:field name=\"url\"><camunda:string>https://api.acme.io</camunda:string></camunda:field>"),

      // ---- async and jobs --------------------------------------------------------------
      Map.entry("camunda:asyncBefore", "true"),
      Map.entry("camunda:asyncAfter", "true"),
      Map.entry("camunda:exclusive", "true"),
      Map.entry("camunda:jobPriority", "100"),
      Map.entry("camunda:failedJobRetryTimeCycle", "R3/PT10M"),

      // ---- listeners, mappings, metadata -----------------------------------------------
      Map.entry("camunda:executionListener",
          "<camunda:executionListener event=\"start\" delegateExpression=\"${auditListener}\" />"),
      Map.entry("camunda:inputOutput",
          "<camunda:inputParameter name=\"amount\">${order.total}</camunda:inputParameter>"),
      Map.entry("camunda:properties",
          "<camunda:property name=\"owner\" value=\"payments-team\" />"),

      // ---- call activity ----------------------------------------------------------------
      Map.entry("calledElement", "credit-check"),
      Map.entry("camunda:calledElementVersion", "3"),
      Map.entry("camunda:calledElementVersionTag", "2.1"),
      Map.entry("camunda:in", "<camunda:in source=\"customerId\" target=\"customerId\" />"),
      Map.entry("camunda:out", "<camunda:out variables=\"all\" />"),
      Map.entry("camunda:variableMappingDelegateExpression", "${orderVariableMapping}"),

      // ---- decisions ---------------------------------------------------------------------
      Map.entry("camunda:decisionRef", "riskRating"),
      Map.entry("camunda:decisionRefVersion", "4"),

      // ---- scripts -----------------------------------------------------------------------
      Map.entry("scriptFormat", "groovy"),
      Map.entry("bpmn:script", "sum = a + b"),
      Map.entry("camunda:resource", "scripts/calculate-total.groovy"),

      // ---- timers and conditions ----------------------------------------------------------
      Map.entry("bpmn:timeDuration", "PT15M"),
      Map.entry("bpmn:timeDate", "2026-09-01T10:00:00Z"),
      Map.entry("bpmn:timeCycle", "R5/PT10M"),
      Map.entry("bpmn:condition", "${amount > 1000}"),
      Map.entry("bpmn:conditionExpression", "${approved}"),
      Map.entry("bpmn:completionCondition", "${nrOfCompletedInstances >= 3}"),
      Map.entry("camunda:variableName", "amount"),
      Map.entry("camunda:variableEvents", "create,update"),

      // ---- errors and escalation ------------------------------------------------------------
      Map.entry("camunda:errorCodeVariable", "errorCode"),
      Map.entry("camunda:errorMessageVariable", "errorMessage"),
      Map.entry("camunda:escalationCodeVariable", "escalationCode"),
      Map.entry("camunda:initiator", "starterId"),

      // ---- references ------------------------------------------------------------------------
      Map.entry("default", "Flow_rejected"),
      Map.entry("sourceRef", "Gateway_approved"),
      Map.entry("targetRef", "Task_ShipOrder"),
      Map.entry("attachedToRef", "Task_ApproveOrder"),
      Map.entry("messageRef", "Message_OrderPlaced"),
      Map.entry("errorRef", "Error_PaymentDeclined"),
      Map.entry("signalRef", "Signal_OrderCancelled"),
      Map.entry("processRef", "order-fulfilment"),
      Map.entry("dataObjectRef", "DataObject_Order"),

      // ---- flags and shape-specific -------------------------------------------------------------
      Map.entry("cancelActivity", "false"),
      Map.entry("isInterrupting", "false"),
      Map.entry("triggeredByEvent", "true"),
      Map.entry("isCollection", "true"),
      Map.entry("isExpanded", "false"),
      Map.entry("isMarkerVisible", "true"),
      Map.entry("bpmn:text", "SLA: two working days"),
      Map.entry("textFormat", "text/plain"));

  private PropertyExamples() {
  }

  public static Set<String> exampledNames() {
    return EXAMPLES.keySet();
  }

  /** Null when there is no value worth showing - an enum's allowedValues already serve as one. */
  public static String exampleFor(String name) {
    return EXAMPLES.get(name);
  }
}
