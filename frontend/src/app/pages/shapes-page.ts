import { Component, computed, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { asyncState } from '../core/async-state';
import { SearchStore } from '../core/search';
import { Breadcrumbs, DocPage, Lozenge, Panel } from '../ui/doc';
import { EmptyState, ErrorState, Spinner } from '../ui/states';
import { CatalogueStore } from '../core/catalogue-store';
import { bpmnIcon } from '../ui/bpmn-icon';

/**
 * The catalogue index, as a documentation table rather than a dashboard of cards.
 *
 * Filters live in the URL - bound straight to inputs by withComponentInputBinding - so a filtered
 * view is linkable: /shapes?category=GATEWAY&executable=false is a valid thing to send someone.
 */
@Component({
  selector: 'app-shapes-page',
  imports: [RouterLink, Breadcrumbs, DocPage, Lozenge, Panel, EmptyState, ErrorState, Spinner],
  template: `
    @let result = shapes();
    @if (result.error) {
      <app-error-state [message]="result.error" />
    } @else {
      <app-breadcrumbs [trail]="trail()" />
      <app-doc-page [heading]="heading()" [lead]="lead()">
        <ng-container meta>
          <app-lozenge colour="blue">{{
            result.loading ? '…' : (result.data?.shapeCount ?? 0) + ' shapes'
          }}</app-lozenge>
          @if (search.debounced()) {
            <span class="text-ink-subtle text-xs">matching “{{ search.debounced() }}”</span>
          }
        </ng-container>

        <div class="flex flex-wrap items-center gap-2">
          <span class="text-ink-subtle dark:text-bodydark2 text-xs font-semibold tracking-wide uppercase">Show</span>
          @for (option of FILTERS; track option.label) {
            <button type="button" (click)="setExecutable(option.value)" [class]="filterClass(option.value)">
              {{ option.label }}
            </button>
          }
        </div>

        @if (executable() === 'false') {
          <app-panel kind="warning" heading="These are drawings, not instructions">
            A modeller will happily let you draw these, but the Camunda engine ignores them completely — no error,
            nothing happens. Fine for explaining a process to people; not something to build a running model around.
          </app-panel>
        }

        @if (result.loading) {
          <app-spinner />
        } @else if (!result.data?.shapes?.length) {
          <app-empty-state message="Try a different search, or clear the category filter." />
        } @else {
          <div class="border-doc-border dark:border-strokedark overflow-x-auto rounded-sm border">
            <table class="w-full border-collapse text-left">
              <thead>
                <tr class="bg-doc-sunken dark:bg-boxdark-2">
                  <th class="text-ink-subtle dark:text-bodydark2 px-4 py-2.5 text-xs font-bold tracking-wide uppercase">
                    Shape
                  </th>
                  <th
                    class="text-ink-subtle dark:text-bodydark2 hidden px-4 py-2.5 text-xs font-bold tracking-wide uppercase md:table-cell"
                  >
                    What it is
                  </th>
                  <th class="text-ink-subtle dark:text-bodydark2 px-4 py-2.5 text-xs font-bold tracking-wide uppercase">
                    Properties
                  </th>
                </tr>
              </thead>
              <tbody>
                @for (shape of result.data?.shapes ?? []; track shape.id) {
                  <tr
                    class="border-doc-border hover:bg-doc-sunken dark:border-strokedark dark:hover:bg-meta-4/40 border-t"
                  >
                    <td class="px-4 py-3 align-top">
                      <div class="flex items-start gap-2.5">
                        <i
                          class="text-ink-subtle dark:text-bodydark mt-0.5 text-base leading-none"
                          [class]="icon(shape.id)"
                          aria-hidden="true"
                        ></i>
                        <div class="min-w-0">
                          <a [routerLink]="['/shapes', shape.id]" class="text-link font-semibold hover:underline">{{
                            shape.name
                          }}</a>
                          <code class="text-ink-subtle dark:text-bodydark2 mt-0.5 block font-mono text-xs">{{
                            shape.tag
                          }}</code>
                          @if (!shape.executable) {
                            <span class="mt-1.5 inline-block">
                              <app-lozenge colour="yellow">drawing only</app-lozenge>
                            </span>
                          }
                        </div>
                      </div>
                    </td>
                    <td class="text-ink-subtle dark:text-bodydark hidden max-w-xl px-4 py-3 align-top text-sm md:table-cell">
                      {{ shape.summary }}
                    </td>
                    <td class="px-4 py-3 align-top text-sm whitespace-nowrap">{{ shape.propertyCount }}</td>
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
export class ShapesPage {
  /** Bound from the query string by withComponentInputBinding(). */
  readonly category = input<string>();
  readonly executable = input<string>();

  private readonly api = inject(Api);
  private readonly catalogue = inject(CatalogueStore);
  private readonly router = inject(Router);
  protected readonly search = inject(SearchStore);

  protected readonly icon = bpmnIcon;

  protected readonly FILTERS = [
    { label: 'Everything', value: undefined },
    { label: 'The engine runs', value: 'true' },
    { label: 'Drawing only', value: 'false' },
  ] as const;

  private readonly query = computed(() => ({
    category: this.category(),
    q: this.search.debounced() || undefined,
    executable: this.executable() === undefined ? undefined : this.executable() === 'true',
  }));

  protected readonly shapes = asyncState(this.query, (query) => this.api.shapes(query));

  protected readonly heading = computed(() =>
    this.category() ? this.catalogue.labelOf(this.category()!) : 'All shapes',
  );

  protected readonly lead = computed(() =>
    this.category()
      ? undefined
      : 'Every shape a Camunda 7 or Fluxnova .bpmn file can contain. Pick one to see what you can set on it, what it looks like, and the rules it has to follow.',
  );

  protected readonly trail = computed(() => [
    { label: 'Argus Properties', link: '/shapes' },
    { label: 'Shapes', link: '/shapes' },
    ...(this.category() ? [{ label: this.catalogue.labelOf(this.category()!) }] : []),
  ]);

  protected setExecutable(value: string | undefined): void {
    void this.router.navigate([], {
      queryParams: { executable: value ?? null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected filterClass(value: string | undefined): string {
    const base = 'rounded-[3px] border px-2.5 py-1 text-xs font-medium transition';
    return this.executable() === value
      ? `${base} border-link bg-link text-white`
      : `${base} border-doc-border text-ink-subtle hover:border-link hover:text-link dark:border-strokedark dark:text-bodydark`;
  }
}
