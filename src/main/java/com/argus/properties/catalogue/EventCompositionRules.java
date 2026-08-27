package com.argus.properties.catalogue;

import static com.argus.properties.catalogue.model.EventComposition.ANY;
import static com.argus.properties.catalogue.model.EventComposition.BOTH;
import static com.argus.properties.catalogue.model.EventComposition.EVENT_SUB_PROCESS;
import static com.argus.properties.catalogue.model.EventComposition.INTERRUPTING_ONLY;
import static com.argus.properties.catalogue.model.EventComposition.NOT_APPLICABLE;
import static com.argus.properties.catalogue.model.EventComposition.TOP_LEVEL;
import static com.argus.properties.catalogue.model.EventComposition.of;

import com.argus.properties.catalogue.model.EventComposition;
import java.util.List;

/**
 * Which event definitions are legal on which event positions.
 *
 * <p>This is the chart the catalogue was missing. Five event tags and ten definitions suggest fifty
 * combinations; the engine accepts forty-nine of a rather different set, because most positions take
 * only a handful of definitions and several combinations exist only in one direction. A timer
 * catches but never throws. A link is a matched pair and appears nowhere else. An error is thrown at
 * an end event and caught on a boundary, and nowhere in between.
 *
 * <p>Every rule below is what the Camunda 7 parser accepts, not what the BPMN specification permits
 * in principle - the two differ, and the deployment only cares about the first.
 */
final class EventCompositionRules {

  private static final String START = "start-event";
  private static final String CATCH = "intermediate-catch-event";
  private static final String THROW = "intermediate-throw-event";
  private static final String BOUNDARY = "boundary-event";
  private static final String END = "end-event";

  private static final String MESSAGE = "message-event-definition";
  private static final String TIMER = "timer-event-definition";
  private static final String ERROR = "error-event-definition";
  private static final String ESCALATION = "escalation-event-definition";
  private static final String SIGNAL = "signal-event-definition";
  private static final String CONDITIONAL = "conditional-event-definition";
  private static final String LINK = "link-event-definition";
  private static final String COMPENSATE = "compensate-event-definition";
  private static final String CANCEL = "cancel-event-definition";
  private static final String TERMINATE = "terminate-event-definition";

  private EventCompositionRules() {
  }

  static List<EventComposition> all() {
    return List.of(

        // ---------------------------------------------------------------- start, top level
        // Nothing is running yet, so there is nothing to interrupt and no cancelActivity choice.
        of(START, TOP_LEVEL, null, NOT_APPLICABLE),
        of(START, TOP_LEVEL, MESSAGE, NOT_APPLICABLE),
        of(START, TOP_LEVEL, TIMER, NOT_APPLICABLE),
        of(START, TOP_LEVEL, SIGNAL, NOT_APPLICABLE),
        of(START, TOP_LEVEL, CONDITIONAL, NOT_APPLICABLE),

        // ---------------------------------------------------------------- start, event sub-process
        // Here there IS something to interrupt - the enclosing scope - so the axis comes alive, and
        // error and compensate become legal because the scope can raise them.
        of(START, EVENT_SUB_PROCESS, MESSAGE, BOTH),
        of(START, EVENT_SUB_PROCESS, TIMER, BOTH),
        of(START, EVENT_SUB_PROCESS, SIGNAL, BOTH),
        of(START, EVENT_SUB_PROCESS, CONDITIONAL, BOTH),
        of(START, EVENT_SUB_PROCESS, ESCALATION, BOTH),
        of(START, EVENT_SUB_PROCESS, ERROR, INTERRUPTING_ONLY,
            "An error always cancels the scope that raised it; a non-interrupting error start is "
                + "rejected at deployment."),
        of(START, EVENT_SUB_PROCESS, COMPENSATE, NOT_APPLICABLE,
            "Runs only when compensation is thrown for the enclosing scope, never during normal flow."),

        // ---------------------------------------------------------------- intermediate catch
        of(CATCH, ANY, MESSAGE, NOT_APPLICABLE),
        of(CATCH, ANY, TIMER, NOT_APPLICABLE),
        of(CATCH, ANY, SIGNAL, NOT_APPLICABLE),
        of(CATCH, ANY, CONDITIONAL, NOT_APPLICABLE),
        of(CATCH, ANY, LINK, NOT_APPLICABLE,
            "Must be paired with a throwing link of the same name in the same process."),

        // ---------------------------------------------------------------- intermediate throw
        // A throw cannot carry a timer or a condition: you cannot throw the passage of time.
        of(THROW, ANY, null, NOT_APPLICABLE),
        of(THROW, ANY, MESSAGE, NOT_APPLICABLE),
        of(THROW, ANY, SIGNAL, NOT_APPLICABLE),
        of(THROW, ANY, ESCALATION, NOT_APPLICABLE),
        of(THROW, ANY, COMPENSATE, NOT_APPLICABLE),
        of(THROW, ANY, LINK, NOT_APPLICABLE,
            "Must be paired with a catching link of the same name in the same process."),

        // ---------------------------------------------------------------- boundary
        // The largest family, and the one where the interrupting axis matters most.
        of(BOUNDARY, ANY, MESSAGE, BOTH),
        of(BOUNDARY, ANY, TIMER, BOTH),
        of(BOUNDARY, ANY, SIGNAL, BOTH),
        of(BOUNDARY, ANY, CONDITIONAL, BOTH),
        of(BOUNDARY, ANY, ESCALATION, BOTH),
        of(BOUNDARY, ANY, ERROR, INTERRUPTING_ONLY,
            "An error boundary event always cancels its host; cancelActivity='false' is rejected."),
        of(BOUNDARY, ANY, CANCEL, INTERRUPTING_ONLY,
            "Only on a transaction sub-process, and only one per transaction."),
        of(BOUNDARY, ANY, COMPENSATE, NOT_APPLICABLE,
            "Neither interrupting nor non-interrupting: it registers a handler and does nothing "
                + "during normal flow."),

        // ---------------------------------------------------------------- end
        // Everything an end event can raise, plus the two that stop things rather than raise them.
        of(END, ANY, null, NOT_APPLICABLE),
        of(END, ANY, MESSAGE, NOT_APPLICABLE),
        of(END, ANY, SIGNAL, NOT_APPLICABLE),
        of(END, ANY, ERROR, NOT_APPLICABLE),
        of(END, ANY, ESCALATION, NOT_APPLICABLE),
        of(END, ANY, COMPENSATE, NOT_APPLICABLE),
        of(END, ANY, TERMINATE, NOT_APPLICABLE),
        of(END, ANY, CANCEL, NOT_APPLICABLE,
            "Only inside a transaction sub-process, where it triggers the cancel boundary event."));
  }
}
