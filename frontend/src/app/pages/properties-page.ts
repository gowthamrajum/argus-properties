import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { asyncState } from '../core/async-state';
import { of } from 'rxjs';
import type { PropertyUsage } from '../core/models';
import { SearchStore } from '../core/search';
import { Breadcrumbs, DocPage, Lozenge, Panel, type LozengeColour } from '../ui/doc';
import { EmptyState, ErrorState, Spinner } from '../ui/states';

const NAMESPACE_COLOUR: Record<string, LozengeColour> = {
  bpmn: 'blue',
  camunda: 'green',
  bpmndi: 'purple',
};

const EMPTY_USAGE: PropertyUsage = {
  name: '',
  label: '',
  namespace: 'bpmn',
  kind: 'ATTRIBUTE',
  shapeCount: 0,
  occurrences: [],
};

const KINDS = [
  { label: 'Everything', value: undefined },
  { label: 'Attributes', value: 'ATTRIBUTE' },
  { label: 'Extension elements', value: 'EXTENSION_ELEMENT' },
  { label: 'Child elements', value: 'CHILD_ELEMENT' },
  { label: 'Diagram only', value: 'DI_ATTRIBUTE' },
] as const;

/**
 * The catalogue indexed by property rather than by shape.
 *
 * <p>Browsing shape-first answers "what can I set here?" but not the question people actually
 * arrive with - "which shapes support task listeners?" Answering that by opening fifty-one pages
 * is not answering it. Rows expand to the full list of shapes, fetched on demand because the
 * listing is deliberately a summary.
 */
@Component({
  selector: 'app-properties-page',
  imports: [RouterLink, Breadcrumbs, DocPage, Lozenge, Panel, EmptyState, ErrorState, Spinner],
  template: `
    @let result = properties();
    @if (result.error) {
      <app-error-state [message]="result.error" />
    } @else {
      <app-breadcrumbs [trail]="[{ label: 'Argus Properties', link: '/shapes' }, { label: 'Property index' }]" />
      <app-doc-page
        heading="Every property, and where it applies"
        lead="The same catalogue read the other way round. Start from a setting you have heard of and find every shape that accepts it — rather than opening shapes one at a time hoping to spot it."
      >
        <ng-container meta>
          <app-lozenge colour="blue">{{ result.loading ? '…' : (result.data ?? []).length }} properties</app-lozenge>
        </ng-container>

        <app-panel kind="info" heading="Looking for listeners?">
          <p>
            Filter to <strong>Extension elements</strong> below. Listeners, input/output mapping and
            generated form fields are not attributes — they are blocks of configuration inside
            <code class="font-mono">&lt;bpmn:extensionElements&gt;</code>, which is why they are easy to
            miss in a list sorted by name. For the events each listener offers — start, end, take,
            create, assignment and the rest — see
            <a routerLink="/listeners" class="text-link hover:underline">Listeners</a>.
          </p>
        </app-panel>

        <div class="flex flex-wrap items-center gap-2">
          <span class="text-ink-subtle dark:text-bodydark2 text-xs font-semibold tracking-wide uppercase">Kind</span>
          @for (option of KINDS; track option.label) {
            <button type="button" (click)="kind.set(option.value)" [class]="chip(kind() === option.value)">
              {{ option.label }}
            </button>
          }
        </div>

        @if (result.loading) {
          <app-spinner />
        } @else if (!(result.data ?? []).length) {
          <app-empty-state message="Nothing matches that filter or search." />
        } @else {
          <div class="border-doc-border dark:border-strokedark overflow-x-auto rounded-sm border">
            <table class="w-full border-collapse text-left">
              <thead>
                <tr class="bg-doc-sunken dark:bg-boxdark-2">
                  @for (column of ['Property', 'Kind', 'Shapes']; track column) {
                    <th class="text-ink-subtle dark:text-bodydark2 px-4 py-2.5 text-xs font-bold tracking-wide uppercase">
                      {{ column }}
                    </th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (usage of result.data ?? []; track usage.name) {
                  <tr
                    (click)="toggle(usage.name)"
                    class="border-doc-border hover:bg-doc-sunken dark:border-strokedark dark:hover:bg-meta-4/40 cursor-pointer border-t align-top"
                  >
                    <td class="px-4 py-3">
                      <div class="flex flex-wrap items-center gap-2">
                        <span class="text-ink font-semibold dark:text-white">{{ usage.label }}</span>
                        <app-lozenge [colour]="colourOf(usage.namespace)">{{ usage.namespace }}</app-lozenge>
                      </div>
                      <code class="text-ink-subtle dark:text-bodydark2 mt-1 block font-mono text-xs">{{
                        usage.name
                      }}</code>

                      @if (open() === usage.name) {
                        @let detail = expanded();
                        @if (detail.loading) {
                          <app-spinner label="Finding shapes" />
                        } @else if (detail.data) {
                          <ul class="mt-3 flex flex-col gap-2">
                            @for (occurrence of detail.data.occurrences; track occurrence.shapeId) {
                              <li class="border-doc-border dark:border-strokedark border-l-2 pl-3">
                                <a
                                  [routerLink]="['/shapes', occurrence.shapeId]"
                                  class="text-link text-sm font-medium hover:underline"
                                  >{{ occurrence.shapeName }}</a
                                >
                                @if (occurrence.inheritedFrom) {
                                  <code class="text-ink-subtle dark:text-bodydark2 ml-2 font-mono text-xs"
                                    >via {{ occurrence.inheritedFrom }}</code
                                  >
                                } @else {
                                  <span class="ml-2"><app-lozenge>declared here</app-lozenge></span>
                                }
                                <p class="text-ink-subtle dark:text-bodydark mt-0.5 max-w-2xl text-xs">
                                  {{ occurrence.description }}
                                </p>
                              </li>
                            }
                          </ul>
                        }
                      }
                    </td>
                    <td class="text-ink-subtle dark:text-bodydark px-4 py-3 text-sm whitespace-nowrap">
                      {{ kindLabel(usage.kind) }}
                    </td>
                    <td class="text-ink dark:text-bodydark1 px-4 py-3 text-sm whitespace-nowrap">
                      {{ usage.shapeCount }}
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </app-doc-page>
    }
  `,
})
export class PropertiesPage {
  private readonly api = inject(Api);
  private readonly search = inject(SearchStore);

  protected readonly KINDS = KINDS;
  protected readonly kind = signal<string | undefined>(undefined);
  protected readonly open = signal<string | null>(null);

  private readonly query = computed(() => ({ kind: this.kind(), q: this.search.debounced() || undefined }));
  protected readonly properties = asyncState(this.query, (q) => this.api.propertyIndex(q));

  /** Occurrences are fetched only when a row opens - the listing is deliberately a summary. */
  protected readonly expanded = asyncState(this.open, (name) =>
    name ? this.api.propertyUsage(name) : of(EMPTY_USAGE),
  );

  protected toggle(name: string): void {
    this.open.update((current) => (current === name ? null : name));
  }

  protected colourOf(namespace: string): LozengeColour {
    return NAMESPACE_COLOUR[namespace] ?? 'default';
  }

  protected kindLabel(kind: string): string {
    return kind.toLowerCase().replace(/_/g, ' ');
  }

  protected chip(active: boolean): string {
    const base = 'rounded-[3px] border px-2.5 py-1 text-xs font-medium transition';
    return active
      ? `${base} border-link bg-link text-white`
      : `${base} border-doc-border text-ink-subtle hover:border-link hover:text-link dark:border-strokedark dark:text-bodydark`;
  }
}
