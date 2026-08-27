package com.argus.properties.catalogue;

import static com.argus.properties.catalogue.PropertyGroups.BASE_ELEMENT;
import static com.argus.properties.catalogue.PropertyGroups.FLOW_ELEMENT;
import static com.argus.properties.catalogue.model.Property.BOOLEAN;
import static com.argus.properties.catalogue.model.Property.IDREF;
import static com.argus.properties.catalogue.model.Property.INTEGER;
import static com.argus.properties.catalogue.model.Property.STRING;
import static com.argus.properties.catalogue.model.Property.attr;
import static com.argus.properties.catalogue.model.Property.child;

import com.argus.properties.catalogue.model.Notation;
import com.argus.properties.catalogue.model.Shape;
import java.util.List;

/**
 * Data shapes and artifacts - everything a modeller can draw that the engine does not execute.
 *
 * <p>Grouped together because they share one property worth knowing up front: none of them are
 * executable in Camunda 7. A data object drawn next to a service task looks like it declares a
 * process variable and does not; a group looks like it contains the shapes inside it and does not.
 * Both are documentation, and the catalogue says so rather than leaving it to be discovered.
 */
final class DataAndArtifactShapes {

  private DataAndArtifactShapes() {
  }

  static List<Shape> all() {
    return List.of(

        Shape.of("data-object-reference", "Data Object", "bpmn:dataObjectReference", Shape.DATA)
            .summary("A reference to a piece of data flowing through the process. Documentation "
                + "only in Camunda 7 - it does not create, name or constrain a process variable.")
            .notExecutable()
            .inherits(BASE_ELEMENT, FLOW_ELEMENT)
            .notation(Notation.shape(36, 50, false, "page with a folded top-right corner",
                List.of("collection: three bars, when the referenced dataObject has isCollection=true")))
            .properties(
                attr("dataObjectRef", IDREF, "The bpmn:dataObject that actually holds the definition."),
                child("bpmn:dataState", "A named state such as 'approved', drawn in square brackets."))
            .constraints("Not bound to process variables by the Camunda 7 engine.")
            .build(),

        Shape.of("data-object", "Data Object (definition)", "bpmn:dataObject", Shape.DATA)
            .summary("The declaration a data object reference points at. Never drawn itself.")
            .notExecutable()
            .inherits(BASE_ELEMENT, FLOW_ELEMENT)
            .notation(Notation.none("Not drawn; its references are."))
            .properties(
                attr("isCollection", BOOLEAN, "false",
                    "Marks the data as a collection, which puts the collection marker on every "
                        + "reference to it."),
                attr("itemSubjectRef", IDREF, "The bpmn:itemDefinition describing its structure."))
            .build(),

        Shape.of("data-store-reference", "Data Store", "bpmn:dataStoreReference", Shape.DATA)
            .summary("Data that outlives the process instance - a database, a file store. "
                + "Documentation only in Camunda 7.")
            .notExecutable()
            .inherits(BASE_ELEMENT, FLOW_ELEMENT)
            .notation(Notation.shape(50, 50, false, "cylinder"))
            .properties(
                attr("dataStoreRef", IDREF, "The bpmn:dataStore declared at definitions level."),
                attr("itemSubjectRef", IDREF, "The bpmn:itemDefinition describing its structure."),
                child("bpmn:dataState", "A named state."))
            .build(),

        Shape.of("data-input", "Data Input", "bpmn:dataInput", Shape.DATA)
            .summary("A formal input in an activity's or process's ioSpecification. Modelling only.")
            .notExecutable()
            .inherits(BASE_ELEMENT)
            .notation(Notation.shape(36, 50, false, "page with an unfilled arrow"))
            .properties(
                attr("name", STRING, "Input name."),
                attr("isCollection", BOOLEAN, "false", "Whether the input is a collection."),
                attr("itemSubjectRef", IDREF, "Structure of the input."))
            .constraints("Lives inside bpmn:ioSpecification. Camunda uses camunda:inputOutput instead.")
            .build(),

        Shape.of("data-output", "Data Output", "bpmn:dataOutput", Shape.DATA)
            .summary("A formal output in an ioSpecification. Modelling only.")
            .notExecutable()
            .inherits(BASE_ELEMENT)
            .notation(Notation.shape(36, 50, false, "page with a filled arrow"))
            .properties(
                attr("name", STRING, "Output name."),
                attr("isCollection", BOOLEAN, "false", "Whether the output is a collection."),
                attr("itemSubjectRef", IDREF, "Structure of the output."))
            .constraints("Lives inside bpmn:ioSpecification. Camunda uses camunda:inputOutput instead.")
            .build(),

        Shape.of("text-annotation", "Text Annotation", "bpmn:textAnnotation", Shape.ARTIFACT)
            .summary("A note on the diagram, attached to a shape with an association.")
            .notExecutable()
            .inherits(BASE_ELEMENT)
            .notation(Notation.shape(100, 30, true, "open square bracket with text to its right"))
            .properties(
                attr("textFormat", STRING, "text/plain", "MIME type of the body."),
                child("bpmn:text", "The note itself."))
            .example("<bpmn:textAnnotation id='Note_1'><bpmn:text>SLA: 2 business days</bpmn:text>"
                + "</bpmn:textAnnotation>")
            .build(),

        Shape.of("group", "Group", "bpmn:group", Shape.ARTIFACT)
            .summary("A dashed box drawn around related shapes. Visual only - it does not contain "
                + "them in the XML, so the shapes inside remain children of the process and can "
                + "be moved out from under it without any model change.")
            .notExecutable()
            .inherits(BASE_ELEMENT)
            .notation(Notation.shape(300, 300, true, "dashed rounded rectangle"))
            .properties(
                attr("categoryValueRef", IDREF,
                    "Points at a bpmn:categoryValue, which holds the label. The group's own text "
                        + "is therefore not on the group element."))
            .constraints("No containment semantics whatsoever - unlike a pool or a sub-process.")
            .build(),

        Shape.of("data-store", "Data Store (definition)", "bpmn:dataStore", Shape.DATA)
            .summary("The definitions-level declaration a data store reference points at.")
            .notExecutable()
            .inherits(BASE_ELEMENT)
            .notation(Notation.none("Not drawn; its references are."))
            .properties(
                attr("name", STRING, "Store name."),
                attr("capacity", INTEGER, "Declared capacity."),
                attr("isUnlimited", BOOLEAN, "true", "Whether capacity is unbounded."),
                attr("itemSubjectRef", IDREF, "Structure of the stored data."))
            .build());
  }
}
