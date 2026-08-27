package com.argus.properties.catalogue.model;

import java.util.List;

/**
 * Properties shared by a whole family of shapes, declared once.
 *
 * <p>Every flow node in BPMN carries {@code id}, {@code name}, {@code incoming}/{@code outgoing}
 * and - under Camunda - the async/job attributes. Repeating those on all fifty shapes would make
 * the catalogue impossible to keep consistent and would bury the handful of properties that
 * actually distinguish a user task from a service task. Shapes reference groups by id and
 * {@code /shapes/&#123;id&#125;/properties} resolves them, stamping each inherited property with
 * its origin.
 */
public record PropertyGroup(String id, String title, String description, List<Property> properties) {

  public PropertyGroup {
    properties = List.copyOf(properties);
  }
}
