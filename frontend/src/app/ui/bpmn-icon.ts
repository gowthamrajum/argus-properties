/**
 * The bpmn.io palette icon for each shape.
 *
 * These are the class names from `bpmn-font`, the icon font bpmn-js itself uses for its palette
 * and context pad. Using them rather than approximations means the index reads exactly like the
 * modeller's palette - the same glyph in the same weight, so recognising a shape here transfers
 * directly to finding it there.
 */
const ICONS: Record<string, string> = {
  // Tasks
  task: 'bpmn-icon-task',
  'user-task': 'bpmn-icon-user-task',
  'service-task': 'bpmn-icon-service-task',
  'send-task': 'bpmn-icon-send-task',
  'receive-task': 'bpmn-icon-receive-task',
  'manual-task': 'bpmn-icon-manual-task',
  'script-task': 'bpmn-icon-script-task',
  'business-rule-task': 'bpmn-icon-business-rule-task',

  // Containers
  process: 'bpmn-icon-process',
  collaboration: 'bpmn-icon-participant',
  participant: 'bpmn-icon-participant',
  lane: 'bpmn-icon-lane',
  'lane-set': 'bpmn-icon-lane',
  'sub-process': 'bpmn-icon-subprocess-collapsed',
  'event-sub-process': 'bpmn-icon-event-subprocess-expanded',
  transaction: 'bpmn-icon-transaction',
  'ad-hoc-sub-process': 'bpmn-icon-ad-hoc-subprocess',
  'call-activity': 'bpmn-icon-call-activity',

  // Gateways
  'exclusive-gateway': 'bpmn-icon-gateway-xor',
  'parallel-gateway': 'bpmn-icon-gateway-parallel',
  'inclusive-gateway': 'bpmn-icon-gateway-or',
  'event-based-gateway': 'bpmn-icon-gateway-eventbased',
  'complex-gateway': 'bpmn-icon-gateway-complex',

  // Events
  'start-event': 'bpmn-icon-start-event-none',
  'intermediate-catch-event': 'bpmn-icon-intermediate-event-none',
  'intermediate-throw-event': 'bpmn-icon-intermediate-event-none',
  'boundary-event': 'bpmn-icon-intermediate-event-none',
  'end-event': 'bpmn-icon-end-event-none',

  // Event definitions
  'message-event-definition': 'bpmn-icon-message',
  'timer-event-definition': 'bpmn-icon-timer',
  'error-event-definition': 'bpmn-icon-error',
  'escalation-event-definition': 'bpmn-icon-escalation',
  'signal-event-definition': 'bpmn-icon-signal',
  'conditional-event-definition': 'bpmn-icon-conditional',
  'link-event-definition': 'bpmn-icon-link',
  'compensate-event-definition': 'bpmn-icon-compensation',
  'cancel-event-definition': 'bpmn-icon-cancel',
  'terminate-event-definition': 'bpmn-icon-terminate-event',

  // Data and artifacts
  'data-object-reference': 'bpmn-icon-data-object',
  'data-object': 'bpmn-icon-data-object',
  'data-store-reference': 'bpmn-icon-data-store',
  'data-store': 'bpmn-icon-data-store',
  'data-input': 'bpmn-icon-data-input',
  'data-output': 'bpmn-icon-data-output',
  'text-annotation': 'bpmn-icon-text-annotation',
  group: 'bpmn-icon-group',

  // Connections
  'sequence-flow': 'bpmn-icon-connection',
  'message-flow': 'bpmn-icon-connection-multi',
  association: 'bpmn-icon-connection',
  'data-input-association': 'bpmn-icon-connection',
  'data-output-association': 'bpmn-icon-connection',
};

export const bpmnIcon = (shapeId: string): string => ICONS[shapeId] ?? 'bpmn-icon-task';
