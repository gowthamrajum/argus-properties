package com.argus.properties.catalogue.model;

import java.util.List;

/**
 * A family of listener, and every event it can be attached to.
 *
 * @param tag             the extension element, e.g. {@code camunda:taskListener}
 * @param appliesTo       which shapes accept this family at all
 * @param implementations the ways you point a listener at code
 * @param notes           behaviour that is not tied to one particular event
 */
public record ListenerType(String id,
                           String name,
                           String tag,
                           String appliesTo,
                           String inShort,
                           List<ListenerEvent> events,
                           List<String> implementations,
                           List<String> notes) {

  public ListenerType {
    events = List.copyOf(events);
    implementations = List.copyOf(implementations);
    notes = notes == null ? List.of() : List.copyOf(notes);
  }
}
