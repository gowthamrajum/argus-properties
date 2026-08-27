package com.argus.properties.catalogue;

import com.argus.properties.catalogue.model.Concept;
import java.util.List;

/**
 * The vocabulary, in plain language.
 *
 * <p>The catalogue is precise but assumes you already know what a "flow node" or an "extension
 * element" is. Someone opening a BPMN file for the first time does not, and telling them to read
 * the OMG specification is not an answer. These entries are the answer: short, conversational, and
 * anchored to something concrete every time.
 *
 * <p>Served from the API rather than hard-coded in the UI so the service stays the single source
 * of truth - the same reason the shapes themselves live here.
 */
final class Concepts {

  private Concepts() {
  }

  static List<Concept> all() {
    return List.of(

        new Concept("shape", "Shape",
            "Anything you can drag onto the canvas in a BPMN modeller.",
            "If you can see it in the diagram, it is a shape: the rounded box someone fills in, "
                + "the diamond where the path splits, the circle where things start. Underneath, "
                + "each one is an XML element in the .bpmn file - so a shape is really two things "
                + "at once, a picture and a piece of a document.",
            "The box labelled \"Approve order\" is a User Task. In the file it is "
                + "<bpmn:userTask id=\"Task_ApproveOrder\" name=\"Approve order\" />.",
            List.of("property", "category", "notation")),

        new Concept("property", "Property",
            "Something you can set on a shape to change what it does.",
            "Drawing a User Task says \"a person does this\". It does not say who, by when, or on "
                + "which form. Properties are how you answer those questions. In a modeller they "
                + "are the fields in the panel on the right; in the file they are attributes and "
                + "nested elements on that shape's XML.",
            "Setting the Assignee property to demo writes camunda:assignee=\"demo\" onto the task, "
                + "and the task now shows up in that person's list instead of a shared queue.",
            List.of("label-and-tag", "namespace", "property-group")),

        new Concept("label-and-tag", "Label and XML tag",
            "Every property has two names: one to read, and one to type.",
            "The XML name is exact but hard going - nobody says \"camunda colon is startable in "
                + "tasklist\" out loud. So the catalogue shows a friendly label first and keeps "
                + "the XML name beside it as a tag. Use the label when you are talking to a "
                + "person, and the tag when you are writing the file.",
            "\"Retry time cycle\" is the label. camunda:failedJobRetryTimeCycle is the tag you "
                + "actually put in the XML. Same property.",
            List.of("property", "namespace")),

        new Concept("namespace", "Namespace: BPMN vs Camunda",
            "Whether a property is standard BPMN, or a Camunda-specific addition.",
            "BPMN is an open standard, and it deliberately says nothing about how a task gets "
                + "done - that would tie the drawing to one vendor. Camunda fills the gap with "
                + "its own properties, all prefixed camunda:. The practical difference: a bpmn: "
                + "property travels to any BPMN tool, a camunda: property only means something to "
                + "Camunda. There is also bpmndi:, which only affects how the shape is drawn.",
            "name=\"Approve order\" is standard BPMN and survives anywhere. "
                + "camunda:assignee=\"demo\" only means something to Camunda.",
            List.of("property", "extension-elements")),

        new Concept("extension-elements", "Extension elements",
            "Camunda settings that are too big to fit in an attribute.",
            "An attribute holds one short value. Some settings need more - a listener has an "
                + "event, a class and maybe a script; a form has a list of fields. Those go inside "
                + "a <bpmn:extensionElements> block on the shape instead. It is the standard's "
                + "official \"vendors, put your things here\" container.",
            "An execution listener is not an attribute; it is "
                + "<camunda:executionListener event=\"start\" delegateExpression=\"${audit}\" /> "
                + "tucked inside <bpmn:extensionElements> on the task.",
            List.of("property", "namespace")),

        new Concept("property-group", "Property group (inheritance)",
            "A set of properties that many shapes share, written down once.",
            "Almost every shape has an id, a name, and Camunda's async settings. Repeating all of "
                + "that on fifty shapes would bury the handful of properties that actually make a "
                + "User Task different from a Service Task. So shared properties live in groups, "
                + "and each shape lists the groups it inherits. When you look at a shape's "
                + "properties you get both - inherited ones first, each marked with where it came "
                + "from.",
            "A User Task inherits camunda:asyncBefore from the camunda-async group, and declares "
                + "camunda:assignee itself. Filtering to \"own only\" hides the first and keeps "
                + "the second.",
            List.of("property", "shape")),

        new Concept("category", "Category",
            "The family a shape belongs to, matching the modeller's palette.",
            "Tasks are things that do work. Gateways are where a path splits or joins. Events are "
                + "things that happen. Containers hold other shapes. Connections join them up. "
                + "Data and Artifacts are documentation. Knowing the category tells you roughly "
                + "what a shape does before you read a word about it.",
            "Exclusive Gateway, Parallel Gateway and Inclusive Gateway are all in the Gateway "
                + "category - all drawn as diamonds, all deciding where the path goes next.",
            List.of("shape", "executable")),

        new Concept("executable", "Executable vs modelling only",
            "Whether the engine actually does anything with the shape.",
            "A modeller will let you draw shapes the Camunda engine ignores completely. They are "
                + "still useful for explaining a process to people - but if you build a model "
                + "around one expecting it to run, nothing happens, and there is no warning. "
                + "Worth checking before you rely on a shape rather than after.",
            "A Data Object drawn next to a task looks like it declares a variable. It does not - "
                + "Camunda never reads it. Use an input/output mapping if you want real data.",
            List.of("shape", "category")),

        new Concept("notation", "Notation",
            "How the shape is drawn, and how big a modeller makes it.",
            "The picture and the meaning are stored separately in a .bpmn file. The meaning lives "
                + "in the process; the geometry lives in a separate diagram section, matched up by "
                + "id. That split is why you need the notation if you are generating a file "
                + "yourself - without it, the file is valid and opens as a blank canvas.",
            "An event is a 36x36 circle; a task is a 100x80 rounded rectangle. Those exact numbers "
                + "go into the diagram section as <dc:Bounds width=\"36\" height=\"36\" ... />.",
            List.of("shape", "constraint")),

        new Concept("constraint", "Constraint",
            "A rule the file must follow that no single property expresses.",
            "Some rules are not about one property having the right value - they are about "
                + "combinations, or about where a shape is allowed to sit. Those cannot be a "
                + "property, so they are listed separately on each shape. Breaking one usually "
                + "means the deployment is rejected, not that something behaves oddly.",
            "\"A boundary event has no incoming sequence flow\" is a constraint. There is no "
                + "property to set wrongly - the shape is simply wired up in a way the engine "
                + "will not accept.",
            List.of("shape", "executable")));
  }
}
