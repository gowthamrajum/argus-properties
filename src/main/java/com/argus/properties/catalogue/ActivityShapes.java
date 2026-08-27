package com.argus.properties.catalogue;

import static com.argus.properties.catalogue.PropertyGroups.ACTIVITY;
import static com.argus.properties.catalogue.PropertyGroups.BASE_ELEMENT;
import static com.argus.properties.catalogue.PropertyGroups.CAMUNDA_ASYNC;
import static com.argus.properties.catalogue.PropertyGroups.CAMUNDA_EXTENSIONS;
import static com.argus.properties.catalogue.PropertyGroups.CAMUNDA_IO_MAPPING;
import static com.argus.properties.catalogue.PropertyGroups.CAMUNDA_IMPLEMENTATION;
import static com.argus.properties.catalogue.PropertyGroups.FLOW_ELEMENT;
import static com.argus.properties.catalogue.PropertyGroups.FLOW_NODE;
import static com.argus.properties.catalogue.model.Property.BOOLEAN;
import static com.argus.properties.catalogue.model.Property.EXPRESSION;
import static com.argus.properties.catalogue.model.Property.IDREF;
import static com.argus.properties.catalogue.model.Property.INTEGER;
import static com.argus.properties.catalogue.model.Property.STRING;
import static com.argus.properties.catalogue.model.Property.attr;
import static com.argus.properties.catalogue.model.Property.child;
import static com.argus.properties.catalogue.model.Property.choice;
import static com.argus.properties.catalogue.model.Property.ext;

import com.argus.properties.catalogue.model.Notation;
import com.argus.properties.catalogue.model.Shape;
import java.util.List;

/**
 * Everything that does work: the eight task types, plus the call activity.
 *
 * <p>Named for BPMN's own supertype rather than for tasks. An activity is the thing that can loop,
 * be compensated, carry data associations and be interrupted by a boundary event - and a call
 * activity does all of that, so filing it with the containers said more about it having a plus
 * marker than about what it is.
 *
 * <p>The eight tasks are the same 100x80 rounded rectangle and differ only by icon and by which
 * properties the engine reads. That is precisely why the catalogue is worth having: the shape does
 * not tell you that a send task and a service task are the same thing to the engine, or that a
 * manual task is a no-op that completes the instant a token arrives.
 */
final class ActivityShapes {

  private static final List<String> MARKERS = List.of(
      "multi-instance parallel: three vertical bars, from bpmn:multiInstanceLoopCharacteristics with isSequential=false",
      "multi-instance sequential: three horizontal bars, isSequential=true",
      "loop: circular arrow, from bpmn:standardLoopCharacteristics",
      "compensation: rewind glyph, from isForCompensation=true");

  private ActivityShapes() {
  }

  private static Notation task(String render) {
    return Notation.shape(100, 80, true, render, MARKERS);
  }

  static List<Shape> all() {
    return List.of(

        Shape.of("task", "Task", "bpmn:task", Shape.ACTIVITY)
            .summary("An untyped unit of work. The engine treats it as a pass-through: the token "
                + "arrives and leaves in the same transaction, doing nothing. Useful while "
                + "drafting, a bug once deployed.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, ACTIVITY)
            .notation(task("rounded rectangle, no icon"))
            .example("<bpmn:task id='Task_1' name='Do something' />")
            .build(),

        Shape.of("user-task", "User Task", "bpmn:userTask", Shape.ACTIVITY)
            .summary("Work done by a person. A wait state: the instance stops here until the task "
                + "is completed through Tasklist or the API.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, ACTIVITY)
            .notation(task("rounded rectangle with a person icon, top left"))
            .properties(
                attr("camunda:assignee", EXPRESSION,
                    "The single user the task belongs to. Setting it skips the claim step, which "
                        + "also means no one else can see the task in a group list."),
                attr("camunda:candidateUsers", STRING,
                    "Comma-separated user ids, or an expression. Any of them may claim it."),
                attr("camunda:candidateGroups", STRING,
                    "Comma-separated group ids, or an expression. The usual choice for queue-based work."),
                attr("camunda:dueDate", EXPRESSION,
                    "ISO-8601 date or expression. Purely informational to the engine - nothing "
                        + "happens when it passes. Use a timer boundary event for an actual deadline."),
                attr("camunda:followUpDate", EXPRESSION, "ISO-8601 date or expression, for filtering in Tasklist."),
                attr("camunda:priority", EXPRESSION, "Integer or expression, conventionally 0-100."),
                attr("camunda:formKey", STRING,
                    "Which form to render: embedded:app:forms/x.html, embedded:deployment:x.html, "
                        + "app:..., or camunda-forms:deployment:x.form."),
                attr("camunda:formRef", STRING, "Camunda Forms key (7.15+), the successor to formKey."),
                choice("camunda:formRefBinding", "latest", List.of("latest", "deployment", "version"),
                    "Which version of the referenced form to bind."),
                attr("camunda:formRefVersion", INTEGER, "Required when formRefBinding=version."),
                ext("camunda:formData",
                    "Generated forms: camunda:formField children with type, label, defaultValue, "
                        + "validation constraints and enum values."),
                ext("camunda:taskListener",
                    "Hook on create, assignment, complete, delete, update or timeout. A timeout "
                        + "listener additionally needs an id and a bpmn:timerEventDefinition child."))
            .example("<bpmn:userTask id='Approve' name='Approve order' camunda:candidateGroups='managers' "
                + "camunda:priority='50' camunda:formKey='camunda-forms:deployment:approve.form' />")
            .build(),

        Shape.of("service-task", "Service Task", "bpmn:serviceTask", Shape.ACTIVITY)
            .summary("Work done by software. Synchronous when implemented by a delegate or "
                + "expression; a wait state when camunda:type=external, where a worker polls for it.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, ACTIVITY, CAMUNDA_IMPLEMENTATION)
            .notation(task("rounded rectangle with a gear icon, top left"))
            .properties(
                attr("implementation", STRING, "##WebService",
                    "BPMN's own implementation hint. Camunda ignores it in favour of camunda:*."),
                attr("operationRef", IDREF, "WS-BPEL operation reference. Unused by Camunda."))
            .constraints("Exactly one of camunda:class, camunda:delegateExpression, camunda:expression "
                + "or camunda:type may be set.")
            .example("<bpmn:serviceTask id='Charge' name='Charge card' camunda:type='external' "
                + "camunda:topic='payment' camunda:taskPriority='50' />")
            .build(),

        Shape.of("send-task", "Send Task", "bpmn:sendTask", Shape.ACTIVITY)
            .summary("Sends a message and continues. To the Camunda 7 engine it is a service task "
                + "with a different icon - the messageRef is documentation, not delivery.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, ACTIVITY, CAMUNDA_IMPLEMENTATION)
            .notation(task("rounded rectangle with a filled envelope icon"))
            .properties(
                attr("messageRef", IDREF, "The bpmn:message being sent. Declarative only."),
                attr("implementation", STRING, "##WebService", "Ignored by Camunda."),
                attr("operationRef", IDREF, "Ignored by Camunda."))
            .build(),

        Shape.of("receive-task", "Receive Task", "bpmn:receiveTask", Shape.ACTIVITY)
            .summary("Waits for a message. A genuine wait state, and the one place other than an "
                + "event where message correlation resumes an instance.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, ACTIVITY)
            .notation(task("rounded rectangle with an outlined envelope icon"))
            .properties(
                attr("messageRef", IDREF,
                    "The bpmn:message to correlate. The message's name attribute is the "
                        + "correlation key, not its id."),
                attr("instantiate", BOOLEAN, "false",
                    "true lets the receive task start a process instance rather than only continue one."),
                attr("implementation", STRING, "##WebService", "Ignored by Camunda."),
                attr("operationRef", IDREF, "Ignored by Camunda."))
            .example("<bpmn:receiveTask id='AwaitConfirm' name='Await confirmation' messageRef='Msg_Confirm' />")
            .build(),

        Shape.of("manual-task", "Manual Task", "bpmn:manualTask", Shape.ACTIVITY)
            .summary("Work done by a person outside any system. The engine cannot observe it, so "
                + "it behaves exactly like an untyped task: through in one transaction.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, ACTIVITY)
            .notation(task("rounded rectangle with a hand icon"))
            .build(),

        Shape.of("script-task", "Script Task", "bpmn:scriptTask", Shape.ACTIVITY)
            .summary("Runs a script inside the engine. Convenient and dangerous in equal measure - "
                + "it executes with the engine's own privileges and is not visible to any build.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, ACTIVITY)
            .notation(task("rounded rectangle with a script/scroll icon"))
            .properties(
                attr("scriptFormat", STRING,
                    "JSR-223 engine name: groovy, javascript, python, ruby, juel, feel. Required "
                        + "whenever a script is present."),
                child("bpmn:script", "The inline script body, normally wrapped in CDATA."),
                attr("camunda:resource", STRING,
                    "Path to a deployed or classpath script instead of an inline body - the "
                        + "version-controllable alternative."),
                attr("camunda:resultVariable", STRING,
                    "Process variable that receives the script's return value."))
            .example("<bpmn:scriptTask id='Calc' name='Calculate total' scriptFormat='groovy' "
                + "camunda:resultVariable='total'><bpmn:script>sum = a + b</bpmn:script></bpmn:scriptTask>")
            .build(),

        Shape.of("business-rule-task", "Business Rule Task", "bpmn:businessRuleTask", Shape.ACTIVITY)
            .summary("Evaluates a DMN decision and maps its result into a variable. Falls back to "
                + "the ordinary implementation attributes when it is not calling DMN.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, ACTIVITY, CAMUNDA_IMPLEMENTATION)
            .notation(task("rounded rectangle with a table icon"))
            .properties(
                attr("camunda:decisionRef", EXPRESSION, "Key of the DMN decision to evaluate, or an expression."),
                choice("camunda:decisionRefBinding", "latest",
                    List.of("latest", "deployment", "version", "versionTag"),
                    "Which version of the decision to bind. 'deployment' pins it to the decision "
                        + "deployed alongside this process, which is the only binding that cannot "
                        + "change under a running instance."),
                attr("camunda:decisionRefVersion", INTEGER, "Required when binding=version."),
                attr("camunda:decisionRefVersionTag", STRING, "Required when binding=versionTag."),
                attr("camunda:decisionRefTenantId", STRING, "Resolve the decision in another tenant."),
                choice("camunda:mapDecisionResult", "resultList",
                    List.of("singleEntry", "singleResult", "collectEntries", "resultList"),
                    "How the decision table's output is flattened into camunda:resultVariable. "
                        + "singleEntry gives a bare value; resultList gives a list of maps. The "
                        + "variable it lands in is camunda:resultVariable, inherited from the "
                        + "implementation group."))
            .example("<bpmn:businessRuleTask id='Rate' name='Determine rating' camunda:decisionRef='riskRating' "
                + "camunda:decisionRefBinding='deployment' camunda:mapDecisionResult='singleEntry' "
                + "camunda:resultVariable='rating' />")
            .build(),

        Shape.of("call-activity", "Call Activity", "bpmn:callActivity", Shape.ACTIVITY)
            .summary("Runs another deployed process as a child instance and waits for it. The "
                + "boundary is real: the child has its own instance, its own history and its own "
                + "variable scope, so data crosses only through explicit mappings.")
            .inherits(BASE_ELEMENT, FLOW_ELEMENT, FLOW_NODE, CAMUNDA_ASYNC, CAMUNDA_EXTENSIONS, CAMUNDA_IO_MAPPING, ACTIVITY)
            .notation(Notation.shape(100, 80, true, "rounded rectangle with a thick border",
                List.of("call activity: plus in a box, always shown")))
            .properties(
                attr("calledElement", EXPRESSION,
                    "The process definition KEY to call - not its name and not a file name. An "
                        + "expression is allowed for dynamic dispatch."),
                choice("camunda:calledElementBinding", "latest",
                    List.of("latest", "deployment", "version", "versionTag"),
                    "Which version to call. 'latest' means a redeploy of the child silently "
                        + "changes what running parents will call next; 'deployment' pins it to "
                        + "the version deployed together with the parent."),
                attr("camunda:calledElementVersion", INTEGER, "Required when binding=version."),
                attr("camunda:calledElementVersionTag", STRING, "Required when binding=versionTag."),
                attr("camunda:calledElementTenantId", STRING, "Call into another tenant."),
                choice("camunda:calledElementType", "bpmn", List.of("bpmn", "cmmn"),
                    "Whether the callee is a BPMN process or a CMMN case."),
                attr("camunda:caseRef", STRING, "CMMN case key, when calledElementType=cmmn."),
                choice("camunda:caseBinding", "latest", List.of("latest", "deployment", "version"),
                    "Version binding for the CMMN case."),
                attr("camunda:caseVersion", INTEGER, "Required when caseBinding=version."),
                attr("camunda:caseTenantId", STRING, "Tenant for the CMMN case."),
                attr("camunda:variableMappingClass", STRING,
                    "DelegateVariableMapping implementation, as a code-side alternative to camunda:in/out."),
                attr("camunda:variableMappingDelegateExpression", EXPRESSION,
                    "Expression resolving to a DelegateVariableMapping bean."),
                ext("camunda:in",
                    "Variables passed down into the child. source, sourceExpression, or "
                        + "variables=\"all\"; businessKey passes the parent's business key."),
                ext("camunda:out",
                    "Variables passed back up when the child completes. Without at least one of "
                        + "these, the child's work is invisible to the parent."))
            .constraints("The called process must be deployed independently; a call activity "
                + "cannot reference a sub-process inside the same file.")
            .example("<bpmn:callActivity id='Call_credit' name='Check credit' calledElement='credit-check' "
                + "camunda:calledElementBinding='deployment'><bpmn:extensionElements>"
                + "<camunda:in source='customerId' target='customerId' />"
                + "<camunda:out variables='all' /></bpmn:extensionElements></bpmn:callActivity>")
            .build());
  }
}
