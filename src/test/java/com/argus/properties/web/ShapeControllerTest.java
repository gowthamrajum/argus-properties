package com.argus.properties.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** The wiring: routes, path variables with prefixes in them, and the 404 shape. */
@SpringBootTest
@AutoConfigureMockMvc
class ShapeControllerTest {

  @Autowired
  private MockMvc mvc;

  @Test
  void listsShapes() throws Exception {
    mvc.perform(get("/api/v1/shapes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shapeCount").isNumber())
        .andExpect(jsonPath("$.countsByCategory.ACTIVITY").value(9))
        .andExpect(jsonPath("$.shapes[?(@.id=='user-task')].tag").value("bpmn:userTask"));
  }

  @Test
  void filtersByCategory() throws Exception {
    mvc.perform(get("/api/v1/shapes").param("category", "GATEWAY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shapeCount").value(5))
        .andExpect(jsonPath("$.countsByCategory.GATEWAY").value(5));
  }

  @Test
  void returnsOneShapeInFull() throws Exception {
    mvc.perform(get("/api/v1/shapes/call-activity"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tag").value("bpmn:callActivity"))
        .andExpect(jsonPath("$.notation.defaultWidth").value(100))
        .andExpect(jsonPath("$.constraints").isArray())
        .andExpect(jsonPath("$.xmlExample").isNotEmpty());
  }

  @Test
  void returnsTheEffectivePropertiesOfAShape() throws Exception {
    mvc.perform(get("/api/v1/shapes/user-task/properties"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tag").value("bpmn:userTask"))
        .andExpect(jsonPath("$.properties[?(@.name=='camunda:assignee')]").isNotEmpty())
        .andExpect(jsonPath("$.properties[?(@.name=='id')].inheritedFrom").value("base-element"))
        .andExpect(jsonPath("$.countsByNamespace.camunda").isNumber());
  }

  @Test
  void narrowsPropertiesToOneNamespace() throws Exception {
    mvc.perform(get("/api/v1/shapes/user-task/properties").param("namespace", "camunda"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.countsByNamespace.bpmn").doesNotExist());
  }

  /** A prefixed name is a legal path segment; this pins that Spring does not mangle the colon. */
  @Test
  void resolvesAPrefixedPropertyName() throws Exception {
    mvc.perform(get("/api/v1/shapes/user-task/properties/camunda:assignee"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("camunda:assignee"))
        .andExpect(jsonPath("$.namespace").value("camunda"))
        .andExpect(jsonPath("$.kind").value("ATTRIBUTE"));
  }

  @Test
  void returnsNotationOnItsOwn() throws Exception {
    mvc.perform(get("/api/v1/shapes/exclusive-gateway/notation"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.diElement").value("bpmndi:BPMNShape"))
        .andExpect(jsonPath("$.defaultWidth").value(50))
        .andExpect(jsonPath("$.defaultHeight").value(50));
  }

  @Test
  void listsCategoriesAndPropertyGroups() throws Exception {
    mvc.perform(get("/api/v1/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.category=='ACTIVITY')].shapeCount").value(9))
        .andExpect(jsonPath("$[?(@.category=='ACTIVITY')].label").value("Activities"));

    mvc.perform(get("/api/v1/property-groups/camunda-async"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.properties[?(@.name=='camunda:exclusive')].defaultValue").value("true"));
  }

  @Test
  void listsCapabilities() throws Exception {
    mvc.perform(get("/api/v1/capabilities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='properties')].path").value("/api/v1/shapes/{id}/properties"));
  }

  @Test
  void answersAnUnknownShapeWith404AndAPointer() throws Exception {
    mvc.perform(get("/api/v1/shapes/nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("UNKNOWN_ELEMENT"))
        .andExpect(jsonPath("$.message").value(Matchers.containsString("/api/v1/shapes")))
        .andExpect(jsonPath("$.path").value("/api/v1/shapes/nope"));
  }

  @Test
  void answersAnUnknownPropertyWith404() throws Exception {
    mvc.perform(get("/api/v1/shapes/user-task/properties/camunda:nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("UNKNOWN_ELEMENT"));
  }

  @Test
  void listsEveryDerivedEventShape() throws Exception {
    mvc.perform(get("/api/v1/event-shapes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(49));
  }

  @Test
  void filtersEventShapesByPositionAndInterrupting() throws Exception {
    mvc.perform(get("/api/v1/event-shapes")
            .param("position", "boundary-event").param("interrupting", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[*].interrupting")
            .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));
  }

  @Test
  void servesOneEventShapeWithItsComposedBehaviour() throws Exception {
    mvc.perform(get("/api/v1/event-shapes/non-interrupting-timer-boundary-event"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.positionShapeId").value("boundary-event"))
        .andExpect(jsonPath("$.definitionShapeId").value("timer-event-definition"))
        .andExpect(jsonPath("$.interrupting").value(false))
        .andExpect(jsonPath("$.behaviour.executionKind").value("WAIT_STATE"))
        .andExpect(jsonPath("$.behaviour.outcomes").isNotEmpty());
  }

  @Test
  void answersALegalityCheck() throws Exception {
    mvc.perform(get("/api/v1/event-shapes/check")
            .param("position", "boundary-event").param("definition", "timer-event-definition"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.legal").value(true))
        .andExpect(jsonPath("$.shapeIds.length()").value(2));

    mvc.perform(get("/api/v1/event-shapes/check")
            .param("position", "end-event").param("definition", "timer-event-definition"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.legal").value(false))
        .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("does not accept")));
  }

  @Test
  void servesShapeBehaviourAndFourOhFoursAnUncataloguedOne() throws Exception {
    mvc.perform(get("/api/v1/shapes/user-task/behaviour"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executionKind").value("WAIT_STATE"))
        .andExpect(jsonPath("$.savePoint").value("ALWAYS"));

    mvc.perform(get("/api/v1/shapes/text-annotation/behaviour"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("UNKNOWN_ELEMENT"));
  }
}
