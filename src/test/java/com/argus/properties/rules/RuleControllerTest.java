package com.argus.properties.rules;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The HTTP contract: status codes, validation messages, and the three-paths-one-table promise. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RuleControllerTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private RuleRepository repository;

  @Autowired
  private ObjectMapper json;

  @BeforeEach
  void clear() {
    repository.deleteAll();
  }

  private String body(String shapeId, String code, String kind, String severity) {
    return """
        {"shapeId":"%s","code":"%s",%s"severity":"%s","title":"A title",
         "rationale":"Why it matters.","remediation":"What to change."}
        """.formatted(shapeId, code, kind == null ? "" : "\"kind\":\"" + kind + "\",", severity);
  }

  @Test
  void createsARuleAndPointsAtIt() throws Exception {
    mvc.perform(post("/api/v1/rules").contentType(MediaType.APPLICATION_JSON)
            .content(body("user-task", "NO_ASSIGNMENT", "VIOLATION", "HIGH")))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", Matchers.startsWith("/api/v1/rules/")))
        .andExpect(jsonPath("$.shapeName").value("User Task"))
        .andExpect(jsonPath("$.enabled").value(true));
  }

  /** Three paths, one table: a rule written through /violations is a rule at /rules. */
  @Test
  void showsARuleCreatedThroughViolationsInTheWholeCatalogue() throws Exception {
    mvc.perform(post("/api/v1/violations").contentType(MediaType.APPLICATION_JSON)
            .content(body("user-task", "NO_ASSIGNMENT", null, "HIGH")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kind").value("VIOLATION"));

    mvc.perform(get("/api/v1/rules")).andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/api/v1/violations")).andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/api/v1/findings")).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void roundTripsAnEditAndADelete() throws Exception {
    String created = mvc.perform(post("/api/v1/findings").contentType(MediaType.APPLICATION_JSON)
            .content(body("service-task", "NO_RETRY", null, "MEDIUM")))
        .andReturn().getResponse().getContentAsString();
    long id = json.readTree(created).get("id").asLong();

    mvc.perform(put("/api/v1/findings/" + id).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"shapeId":"service-task","code":"NO_RETRY","severity":"HIGH","title":"Reworded",
                 "rationale":"New reasoning.","remediation":"New instruction.","enabled":false}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.severity").value("HIGH"))
        .andExpect(jsonPath("$.enabled").value(false));

    mvc.perform(delete("/api/v1/findings/" + id)).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/rules/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void rejectsADuplicateCodeWithAConflict() throws Exception {
    mvc.perform(post("/api/v1/rules").contentType(MediaType.APPLICATION_JSON)
        .content(body("user-task", "NO_ASSIGNMENT", "VIOLATION", "HIGH")));

    mvc.perform(post("/api/v1/rules").contentType(MediaType.APPLICATION_JSON)
            .content(body("user-task", "NO_ASSIGNMENT", "FINDING", "LOW")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.message").value(Matchers.containsString("already has a rule")));
  }

  @Test
  void rejectsARuleAgainstAnUnknownShape() throws Exception {
    mvc.perform(post("/api/v1/rules").contentType(MediaType.APPLICATION_JSON)
            .content(body("user-tsak", "X", "FINDING", "LOW")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("UNKNOWN_ELEMENT"));
  }

  /** The validation messages are the documentation a caller actually reads. */
  @Test
  void explainsWhatIsWrongWithABadBody() throws Exception {
    mvc.perform(post("/api/v1/rules").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"shapeId":"user-task","code":"lower case","kind":"FINDING","severity":"LOW",
                 "title":"t","rationale":"r","remediation":"f"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value(Matchers.containsString("SCREAMING_SNAKE_CASE")))
        // The whole point: not Spring's exception dump, which also happens to contain that phrase.
        .andExpect(jsonPath("$.message").value(Matchers.not(Matchers.containsString("org.springframework"))));

    mvc.perform(post("/api/v1/rules").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"shapeId":"user-task","code":"X","kind":"FINDING","severity":"LOW",
                 "title":"t","rationale":"","remediation":"f"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(Matchers.startsWith("rationale: ")));
  }

  @Test
  void listsEveryRuleForOneShapeOnTheShapesOwnPath() throws Exception {
    mvc.perform(post("/api/v1/violations").contentType(MediaType.APPLICATION_JSON)
        .content(body("user-task", "A", null, "HIGH")));
    mvc.perform(post("/api/v1/findings").contentType(MediaType.APPLICATION_JSON)
        .content(body("user-task", "B", null, "LOW")));

    mvc.perform(get("/api/v1/shapes/user-task/rules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }
}
