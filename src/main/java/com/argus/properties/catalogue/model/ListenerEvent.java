package com.argus.properties.catalogue.model;

import java.util.List;

/**
 * One moment a listener can be attached to.
 *
 * <p>"Add an execution listener" is not an instruction anyone can follow - the useful question is
 * <em>when</em>. The event name is the whole configuration: {@code start} and {@code end} do
 * different things, and {@code take} is not available at all on the element you probably had in
 * mind.
 *
 * @param event     the value that goes in the {@code event} attribute
 * @param firesWhen the exact moment, in terms of what the engine is doing
 * @param useFor    what people reach for it for
 * @param caveat    the part that surprises people, or null
 * @param validOn   which shapes accept this event
 */
public record ListenerEvent(String event,
                            String label,
                            String firesWhen,
                            String useFor,
                            String caveat,
                            List<String> validOn) {

  public ListenerEvent {
    validOn = validOn == null ? List.of() : List.copyOf(validOn);
  }
}
