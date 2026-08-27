/**
 * The service's Java records, one-for-one.
 *
 * Fields it serialises with Jackson's non_null inclusion - example, defaultValue, inheritedFrom,
 * xmlExample, diElement - are optional rather than nullable here, because an absent key is exactly
 * how the service says "not set".
 */

export type PropertyKind = 'ATTRIBUTE' | 'CHILD_ELEMENT' | 'EXTENSION_ELEMENT' | 'DI_ATTRIBUTE';
export type Namespace = 'bpmn' | 'camunda' | 'bpmndi';

export interface Property {
  /** The XML name you type into the file, e.g. `camunda:assignee`. */
  name: string;
  /** The human-readable name, e.g. "Assignee". Shown first; `name` sits beside it as a tag. */
  label: string;
  kind: PropertyKind;
  namespace: Namespace;
  type: string;
  required: boolean;
  /** A concrete value you could type. Absent where an enum's allowedValues already serve as one. */
  example?: string;
  defaultValue?: string;
  allowedValues: string[];
  description: string;
  /** Absent when the shape declares the property itself. */
  inheritedFrom?: string;
}

export interface Notation {
  diElement?: string;
  defaultWidth?: number;
  defaultHeight?: number;
  resizable: boolean;
  render: string;
  markers: string[];
}

export interface Shape {
  id: string;
  name: string;
  tag: string;
  category: string;
  summary: string;
  executable: boolean;
  inherits: string[];
  /** Own properties only - the effective set comes from /shapes/{id}/properties. */
  properties: Property[];
  notation: Notation;
  constraints: string[];
  xmlExample?: string;
}

export interface ShapeSummary {
  id: string;
  name: string;
  tag: string;
  category: string;
  summary: string;
  executable: boolean;
  propertyCount: number;
}

export interface ShapesResponse {
  shapeCount: number;
  countsByCategory: Record<string, number>;
  shapes: ShapeSummary[];
}

export interface PropertiesResponse {
  shapeId: string;
  shape: string;
  tag: string;
  propertyCount: number;
  countsByNamespace: Record<string, number>;
  countsByKind: Record<string, number>;
  properties: Property[];
}

export interface CategoryEntry {
  category: string;
  /** How the family is written for people — supplied by the service, not derived here. */
  label: string;
  shapeCount: number;
  shapeIds: string[];
}

export interface Concept {
  id: string;
  term: string;
  inShort: string;
  explanation: string;
  example: string;
  related: string[];
}

export interface PropertyGroup {
  id: string;
  title: string;
  description: string;
  properties: Property[];
}

/*
 * Runtime behaviour and concrete event shapes.
 *
 * Added by a parallel effort on the service: the catalogue says what you can configure, these say
 * what happens when it runs, and which event position/definition combinations actually exist.
 */

export interface Outcome {
  /** WAITING, COMPLETED, STUCK, BPMN_ERROR, INCIDENT and friends. */
  id: string;
  trigger: string;
  effect: string;
  /** Absent when the outcome needs no intervention. */
  recovery?: string;
}

export interface RetryProfile {
  retriesTechnicalFailures: boolean;
  defaultRetries: number;
  configuredBy: string;
  note: string;
}

export interface Behaviour {
  executionKind: string;
  savePoint: string;
  outcomes: Outcome[];
  retries?: RetryProfile;
  notes: string[];
}

export interface EventShape {
  id: string;
  name: string;
  summary: string;
  positionShapeId: string;
  positionTag: string;
  /** Absent for a "none" event, which is a position with no definition at all. */
  definitionShapeId?: string;
  definitionTag?: string;
  context: 'ANY' | 'TOP_LEVEL' | 'EVENT_SUB_PROCESS';
  interrupting?: boolean;
  requires: string[];
  xmlSketch: string;
  behaviour?: Behaviour;
}

/**
 * The catalogue read along the property axis instead of the shape axis.
 *
 * `occurrences` is empty in the listing and populated on `/properties/{name}` — the listing would
 * otherwise be an order of magnitude larger than the index anyone wants to scan.
 */
export interface PropertyUsage {
  name: string;
  label: string;
  namespace: Namespace;
  kind: PropertyKind;
  shapeCount: number;
  occurrences: PropertyOccurrence[];
}

export interface PropertyOccurrence {
  shapeId: string;
  shapeName: string;
  category: string;
  /** Absent when the shape declares the property itself rather than inheriting it. */
  inheritedFrom?: string;
  description: string;
}

/** One moment a listener can be attached to. The event name is the whole configuration. */
export interface ListenerEvent {
  event: string;
  label: string;
  firesWhen: string;
  useFor: string;
  /** The part that surprises people. Absent when there isn't one. */
  caveat?: string;
  validOn: string[];
}

export interface ListenerType {
  id: string;
  name: string;
  tag: string;
  appliesTo: string;
  inShort: string;
  events: ListenerEvent[];
  implementations: string[];
  notes: string[];
}
