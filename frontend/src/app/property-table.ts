import { Component, input, signal } from '@angular/core';
import type { Namespace, Property } from './core/models';
import { Lozenge, type LozengeColour } from './ui/doc';

const NAMESPACE_COLOUR: Record<Namespace, LozengeColour> = {
  bpmn: 'blue',
  camunda: 'green',
  bpmndi: 'purple',
};

const KIND_LABEL: Record<string, string> = {
  ATTRIBUTE: 'attribute',
  CHILD_ELEMENT: 'child element',
  EXTENSION_ELEMENT: 'extension element',
  DI_ATTRIBUTE: 'diagram only',
};

/**
 * One row per property, human name first.
 *
 * The XML name is what you eventually type, but not what you scan a table for - "Retry time cycle"
 * is findable in a way camunda:failedJobRetryTimeCycle is not. So the label leads and the tag sits
 * under it in monospace, which is also the order you need them in: recognise the setting, then
 * copy the attribute.
 */
@Component({
  selector: 'app-property-table',
  imports: [Lozenge],
  template: `
    @if (!properties().length) {
      <p class="text-ink-subtle dark:text-bodydark2 py-8 text-center text-sm">No properties match this filter.</p>
    } @else {
      <div class="border-doc-border dark:border-strokedark overflow-x-auto rounded-sm border">
        <table class="w-full border-collapse text-left">
          <thead>
            <tr class="bg-doc-sunken dark:bg-boxdark-2">
              @for (column of ['Property', 'Example', 'Default', 'Source']; track column) {
                <th class="text-ink-subtle dark:text-bodydark2 px-4 py-2.5 text-xs font-bold tracking-wide uppercase">
                  {{ column }}
                </th>
              }
            </tr>
          </thead>
          <tbody>
            @for (property of properties(); track property.name) {
              @let expanded = open() === property.name;
              <tr
                (click)="toggle(property.name)"
                class="border-doc-border hover:bg-doc-sunken dark:border-strokedark dark:hover:bg-meta-4/40 cursor-pointer border-t align-top"
              >
                <td class="px-4 py-3">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="text-ink font-semibold dark:text-white">{{ property.label }}</span>
                    <app-lozenge [colour]="colourOf(property.namespace)">{{ property.namespace }}</app-lozenge>
                    @if (property.required) {
                      <app-lozenge colour="red">required</app-lozenge>
                    }
                  </div>
                  <!-- What it is called in Camunda: the part you actually paste into the file. -->
                  <code class="text-ink-subtle dark:text-bodydark2 mt-1 block font-mono text-xs">{{
                    property.name
                  }}</code>
                  <p
                    class="text-ink-subtle dark:text-bodydark mt-1.5 max-w-2xl text-sm"
                    [class.line-clamp-1]="!expanded"
                  >
                    {{ property.description }}
                  </p>
                  @if (expanded) {
                    <dl class="mt-3 flex flex-col gap-2 text-xs">
                      <div class="flex gap-2">
                        <dt class="text-ink-subtle dark:text-bodydark2 w-20 shrink-0 font-semibold">Set as</dt>
                        <dd>{{ kindLabel(property.kind) }}</dd>
                      </div>
                      <div class="flex gap-2">
                        <dt class="text-ink-subtle dark:text-bodydark2 w-20 shrink-0 font-semibold">Value</dt>
                        <dd class="font-mono">{{ property.type }}</dd>
                      </div>
                      @if (property.allowedValues.length) {
                        <div class="flex gap-2">
                          <dt class="text-ink-subtle dark:text-bodydark2 w-20 shrink-0 font-semibold">One of</dt>
                          <dd class="flex flex-wrap gap-1.5">
                            @for (value of property.allowedValues; track value) {
                              <code
                                class="rounded-[3px] px-1.5 py-0.5 font-mono"
                                [class]="
                                  value === property.defaultValue
                                    ? 'bg-link/10 font-semibold text-link dark:bg-link/25 dark:text-secondary'
                                    : 'bg-doc-sunken dark:bg-meta-4'
                                "
                                >{{ value }}</code
                              >
                            }
                          </dd>
                        </div>
                      }
                    </dl>
                  }
                </td>
                <td class="px-4 py-3">
                  @if (property.example) {
                    <code
                      class="bg-doc-sunken text-ink dark:bg-meta-4 dark:text-bodydark1 block max-w-xs truncate rounded-[3px] px-1.5 py-0.5 font-mono text-xs"
                      >{{ property.example }}</code
                    >
                  } @else {
                    <span class="text-ink-subtle text-xs">—</span>
                  }
                </td>
                <td class="px-4 py-3">
                  @if (property.defaultValue) {
                    <code class="text-ink font-mono text-xs dark:text-white">{{ property.defaultValue }}</code>
                  } @else {
                    <span class="text-ink-subtle text-xs">—</span>
                  }
                </td>
                <td class="px-4 py-3 whitespace-nowrap">
                  @if (property.inheritedFrom) {
                    <code class="text-ink-subtle dark:text-bodydark2 font-mono text-xs">{{
                      property.inheritedFrom
                    }}</code>
                  } @else {
                    <app-lozenge>own</app-lozenge>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class PropertyTable {
  readonly properties = input.required<Property[]>();
  protected readonly open = signal<string | null>(null);

  protected toggle(name: string): void {
    this.open.update((current) => (current === name ? null : name));
  }

  protected colourOf(namespace: Namespace): LozengeColour {
    return NAMESPACE_COLOUR[namespace] ?? 'default';
  }

  protected kindLabel(kind: string): string {
    return KIND_LABEL[kind] ?? kind;
  }
}
