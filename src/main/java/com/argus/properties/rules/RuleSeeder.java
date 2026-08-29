package com.argus.properties.rules;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A handful of real rules, inserted once when the table is empty.
 *
 * <p>An empty CRUD screen teaches nobody what a rule is meant to look like, and the first one
 * somebody writes tends to set the tone for the rest. These are examples of the shape of a good
 * rule - a reason as well as an instruction - not a default policy, so they are ordinary rows and
 * can be edited or deleted like any other.
 *
 * <p>Seeds only into an empty table, so it never fights an operator who deleted one on purpose.
 * Disable entirely with {@code argus.rules.seed-examples=false}.
 */
@Configuration
public class RuleSeeder {

  private static final Logger log = LoggerFactory.getLogger(RuleSeeder.class);

  @Bean
  ApplicationRunner seedExampleRules(RuleRepository repository,
                                     @Value("${argus.rules.seed-examples:true}") boolean seed) {
    return args -> {
      if (!seed || repository.count() > 0) {
        return;
      }
      List<Rule> examples = List.of(
          new Rule("user-task", "NO_ASSIGNMENT", RuleKind.VIOLATION, Severity.HIGH,
              "User task assigned to nobody",
              "A task with no assignee and no candidate group is created successfully and appears "
                  + "in nobody's list. The instance looks healthy and simply never progresses.",
              "Set camunda:candidateGroups, or camunda:assignee if it belongs to one person.", true),
          new Rule("service-task", "NO_RETRY_CYCLE", RuleKind.FINDING, Severity.MEDIUM,
              "External task with no retry policy",
              "Without camunda:failedJobRetryTimeCycle the engine retries three times with no delay, "
                  + "so a transient outage becomes an incident within milliseconds.",
              "Set camunda:failedJobRetryTimeCycle, e.g. R3/PT10M.", true),
          new Rule("call-activity", "LATEST_BINDING", RuleKind.FINDING, Severity.MEDIUM,
              "Call activity bound to latest",
              "With binding=latest, redeploying the child silently changes what running parents call "
                  + "next. The parent was tested against a version it may never run again.",
              "Use camunda:calledElementBinding=deployment to pin the child to the version deployed "
                  + "alongside this process.", true),
          new Rule("process", "MISSING_HISTORY_TTL", RuleKind.VIOLATION, Severity.HIGH,
              "No history time to live",
              "Camunda 7.20+ refuses the deployment outright when historyTimeToLive is missing and no "
                  + "engine default is set, so this fails at deploy time rather than at run time.",
              "Set camunda:historyTimeToLive on the process, e.g. P180D.", true),
          new Rule("exclusive-gateway", "NO_DEFAULT_FLOW", RuleKind.FINDING, Severity.MEDIUM,
              "Exclusive gateway with no default flow",
              "A token that matches none of the conditions throws instead of continuing, and the "
                  + "condition set is rarely as exhaustive as it looks.",
              "Set the default attribute to the branch that should be taken when nothing matches.", true));

      repository.saveAll(examples);
      log.info("Seeded {} example rules. Disable with argus.rules.seed-examples=false", examples.size());
    };
  }
}
