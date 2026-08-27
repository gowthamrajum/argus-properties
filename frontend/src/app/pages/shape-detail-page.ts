import { Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api';
import type { Outcome, Property } from '../core/models';
import { asyncState } from '../core/async-state';
import { PropertyTable } from '../property-table';
import { BpmnCanvas } from '../bpmn-canvas';
import { bpmnIcon } from '../ui/bpmn-icon';
import { Breadcrumbs, CodeMacro, DocPage, Lozenge, type LozengeColour, Panel, Section } from '../ui/doc';
import { ErrorState, Spinner } from '../ui/states';
import { CatalogueStore } from '../core/catalogue-store';

const CATEGORY_COLOUR: Record<string, LozengeColour> = {
  CONTAINER: 'blue',
  TASK: 'green',
  GATEWAY: 'yellow',
  EVENT: 'purple',
  EVENT_DEFINITION: 'purple',
  DATA: 'default',
  ARTIFACT: 'default',
  CONNECTION: 'red',
};

@Component({
  selector: 'app-shape-detail-page',
  imports: [
    RouterLink,
    Breadcrumbs,
    CodeMacro,
    DocPage,
    Lozenge,
    Panel,
    Section,
    PropertyTable,
    BpmnCanvas,
    ErrorState,
    Spinner,
  ],
  template: `
    @let result = shape();
    @if (result.error) {
      <app-error-state [message]="result.error" />
    } @else if (result.loading || !result.data) {
      <app-spinner />
    } @else {
      @let s = result.data;
      @let props = properties();
      <app-breadcrumbs [trail]="trail()" />
      <app-doc-page [heading]="s.name" [lead]="s.summary" [toc]="toc()">
        <ng-container meta>
          <i class="text-ink dark:text-bodydark1 text-lg leading-none" [class]="icon(s.id)" aria-hidden="true"></i>
          <app-lozenge [colour]="colourOf(s.category)">{{ label(s.category) }}</app-lozenge>
          @if (s.executable) {
            <app-lozenge colour="green">the engine runs this</app-lozenge>
          } @else {
            <app-lozenge colour="yellow">drawing only</app-lozenge>
          }
          <code class="text-ink-subtle dark:text-bodydark2 font-mono text-xs">{{ s.tag }}</code>
        </ng-container>

        <app-section anchor="what-it-is" heading="What it is">
          <!-- Rendered by bpmn-js from real BPMN the service generates, not an approximation. -->
          <app-bpmn-canvas [shapeId]="s.id" [height]="200" [caption]="previewCaption()" />

          <div class="mt-6 flex flex-col gap-6">
            <dl class="divide-doc-border dark:divide-strokedark min-w-0 flex-1 divide-y text-sm">
              <div class="flex flex-col gap-1 py-2.5 sm:flex-row sm:gap-4">
                <dt class="text-ink-subtle dark:text-bodydark2 w-56 shrink-0 font-semibold">In the XML</dt>
                <dd class="text-ink dark:text-bodydark1 font-mono">{{ s.tag }}</dd>
              </div>
              <div class="flex flex-col gap-1 py-2.5 sm:flex-row sm:gap-4">
                <dt class="text-ink-subtle dark:text-bodydark2 w-56 shrink-0 font-semibold">Family</dt>
                <dd class="text-ink dark:text-bodydark1">{{ label(s.category) }}</dd>
              </div>
              <div class="flex flex-col gap-1 py-2.5 sm:flex-row sm:gap-4">
                <dt class="text-ink-subtle dark:text-bodydark2 w-56 shrink-0 font-semibold">Does the engine run it?</dt>
                <dd class="text-ink dark:text-bodydark1">
                  {{
                    s.executable
                      ? 'Yes — Camunda acts on this shape.'
                      : 'No — a modeller will draw it, the engine ignores it.'
                  }}
                </dd>
              </div>
              <div class="flex flex-col gap-1 py-2.5 sm:flex-row sm:gap-4">
                <dt class="text-ink-subtle dark:text-bodydark2 w-56 shrink-0 font-semibold">Things you can set</dt>
                <dd class="text-ink dark:text-bodydark1">
                  {{ props.data?.propertyCount ?? '…' }} in total, {{ s.properties.length }} specific to this shape
                </dd>
              </div>
            </dl>
          </div>

          @if (!s.executable) {
            <app-panel kind="warning" heading="Careful with this one">
              You can draw it, but Camunda will not do anything with it — and it will not warn you. Useful for
              explaining a process to people; not something to build a running model on.
            </app-panel>
          }
        </app-section>

        <app-section
          anchor="properties"
          heading="What you can set"
          lead="Each row is one setting. The bold name is what to call it; the small monospace name underneath is what you actually write in the file."
        >
          <app-panel kind="info" heading="Two kinds of row">
            <p>
              A row whose <strong>Source</strong> says <code class="font-mono">own</code> is specific to this shape.
              Anything else names a
              <a routerLink="/property-groups" class="text-link hover:underline">shared group</a> — a set of properties
              most shapes carry. Use <em>Only this shape</em> below to hide the shared ones.
            </p>
          </app-panel>

          <div class="mb-4 flex flex-wrap items-center gap-2">
            <span class="text-ink-subtle dark:text-bodydark2 text-xs font-semibold tracking-wide uppercase">Filter</span>
            <button type="button" (click)="toggleOwn()" [class]="toggleClass(own())">Only this shape</button>
            <button type="button" (click)="toggleExtensions()" [class]="toggleClass(extensionsOnly())">
              Listeners &amp; blocks
            </button>
            @for (option of NAMESPACES; track option.value) {
              <button type="button" (click)="toggleNamespace(option.value)" [class]="toggleClass(namespace() === option.value)">
                {{ option.label }}
              </button>
            }
          </div>

          @if (props.loading) {
            <app-spinner label="Loading properties" />
          } @else if (props.error) {
            <app-error-state [message]="props.error" />
          } @else {
            <app-property-table [properties]="visibleProperties()" />
          }
        </app-section>

        <app-section
          anchor="notation"
          heading="How it is drawn"
          lead="A .bpmn file keeps the picture separate from the meaning. If you are writing a file yourself, this is what goes in the diagram half of it."
        >
          <dl class="divide-doc-border dark:divide-strokedark divide-y text-sm">
            <div class="flex flex-col gap-1 py-2.5 sm:flex-row sm:gap-4">
              <dt class="text-ink-subtle dark:text-bodydark2 w-56 shrink-0 font-semibold">Diagram element</dt>
              <dd class="text-ink dark:text-bodydark1 font-mono">{{ s.notation.diElement ?? 'not drawn' }}</dd>
            </div>
            <div class="flex flex-col gap-1 py-2.5 sm:flex-row sm:gap-4">
              <dt class="text-ink-subtle dark:text-bodydark2 w-56 shrink-0 font-semibold">Size a modeller uses</dt>
              <dd class="text-ink dark:text-bodydark1">{{ sizeText() }}</dd>
            </div>
            <div class="flex flex-col gap-1 py-2.5 sm:flex-row sm:gap-4">
              <dt class="text-ink-subtle dark:text-bodydark2 w-56 shrink-0 font-semibold">Looks like</dt>
              <dd class="text-ink dark:text-bodydark1">{{ s.notation.render }}</dd>
            </div>
          </dl>
          @if (s.notation.markers.length) {
            <h3 class="text-ink-subtle dark:text-bodydark2 mt-6 mb-2 text-sm font-bold tracking-wide uppercase">
              Little icons that can appear on it
            </h3>
            <ul class="flex flex-col gap-2">
              @for (marker of s.notation.markers; track marker) {
                <li class="border-doc-border text-ink-subtle dark:border-strokedark dark:text-bodydark border-l-2 pl-3 text-sm">
                  {{ marker }}
                </li>
              }
            </ul>
          }
        </app-section>

        @let runtime = behaviour().data;
        @if (runtime) {
          <app-section
            anchor="behaviour"
            heading="What happens at runtime"
            lead="How the engine executes this shape, and every way it can end — including the ways that look like nothing went wrong."
          >
            <dl class="divide-doc-border dark:divide-strokedark mb-5 divide-y text-sm">
              <div class="flex flex-col gap-1 py-2.5 sm:flex-row sm:gap-4">
                <dt class="text-ink-subtle dark:text-bodydark2 w-56 shrink-0 font-semibold">How it executes</dt>
                <dd class="text-ink dark:text-bodydark1">{{ readable(runtime.executionKind) }}</dd>
              </div>
              <div class="flex flex-col gap-1 py-2.5 sm:flex-row sm:gap-4">
                <dt class="text-ink-subtle dark:text-bodydark2 w-56 shrink-0 font-semibold">Is it a save point?</dt>
                <dd class="text-ink dark:text-bodydark1">{{ readable(runtime.savePoint) }}</dd>
              </div>
            </dl>

            <h3 class="text-ink-subtle dark:text-bodydark2 mb-3 text-sm font-bold tracking-wide uppercase">
              Ways it can end
            </h3>
            <ul class="flex flex-col gap-4">
              @for (outcome of runtime.outcomes; track outcome.id) {
                <li class="border-doc-border dark:border-strokedark rounded-sm border p-4">
                  <app-lozenge [colour]="outcomeColour(outcome)">{{ readable(outcome.id) }}</app-lozenge>
                  <p class="text-ink dark:text-bodydark1 mt-2 text-sm"><strong>When:</strong> {{ outcome.trigger }}</p>
                  <p class="text-ink-subtle dark:text-bodydark mt-1 text-sm">{{ outcome.effect }}</p>
                  @if (outcome.recovery) {
                    <p class="text-ink-subtle dark:text-bodydark mt-1.5 text-sm">
                      <strong>Getting out of it:</strong> {{ outcome.recovery }}
                    </p>
                  }
                </li>
              }
            </ul>

            @if (runtime.retries) {
              <app-panel kind="note" heading="Retries">
                <p>
                  {{ runtime.retries.defaultRetries }} attempts by default, configured with
                  <code class="font-mono">{{ runtime.retries.configuredBy }}</code
                  >. {{ runtime.retries.note }}
                </p>
              </app-panel>
            }
            @for (note of runtime.notes; track note) {
              <app-panel kind="info">{{ note }}</app-panel>
            }
          </app-section>
        }

        @if (s.constraints.length) {
          <app-section
            anchor="rules"
            heading="Rules to follow"
            lead="These are not settings you can get wrong — they are ways of wiring the shape up that Camunda will reject."
          >
            @for (constraint of s.constraints; track constraint) {
              <app-panel kind="warning">{{ constraint }}</app-panel>
            }
          </app-section>
        }

        @if (s.xmlExample) {
          <app-section anchor="example" heading="Example" lead="The smallest valid version of this shape.">
            <app-code-macro heading="XML" [code]="formatXml(s.xmlExample)" />
          </app-section>
        }
      </app-doc-page>
    }
  `,
})
export class ShapeDetailPage {
  /** Bound from the route path by withComponentInputBinding(). */
  readonly id = input.required<string>();

  private readonly api = inject(Api);
  private readonly catalogue = inject(CatalogueStore);
  protected readonly own = signal(false);
  /** Extension elements - listeners, input/output mapping, form data - filtered client-side. */
  protected readonly extensionsOnly = signal(false);
  protected readonly namespace = signal<string | undefined>(undefined);

  protected readonly NAMESPACES = [
    { label: 'Standard BPMN', value: 'bpmn' },
    { label: 'Camunda only', value: 'camunda' },
    { label: 'Drawing only', value: 'bpmndi' },
  ] as const;

  protected readonly shape = asyncState(this.id, (id) => this.api.shape(id));
  protected readonly behaviour = asyncState(this.id, (id) => this.api.behaviour(id));

  private readonly propertyQuery = computed(() => ({
    id: this.id(),
    own: this.own(),
    namespace: this.namespace(),
  }));

  protected readonly properties = asyncState(this.propertyQuery, (query) =>
    this.api.properties(query.id, { own: query.own, namespace: query.namespace }),
  );

  protected readonly label = (category: string) => this.catalogue.labelOf(category);
  protected readonly icon = bpmnIcon;

  /** Says where the picture came from, and repeats the bounds it was drawn at. */
  protected readonly previewCaption = computed(() => {
    const notation = this.shape().data?.notation;
    const size = notation?.defaultWidth ? ` at its default ${notation.defaultWidth} × ${notation.defaultHeight}` : '';
    return `Rendered by bpmn-js from a real .bpmn document${size}.`;
  });

  protected readonly toc = computed(() => {
    const s = this.shape().data;
    return [
      { id: 'what-it-is', label: 'What it is' },
      { id: 'properties', label: 'What you can set' },
      { id: 'notation', label: 'How it is drawn' },
      ...(this.behaviour().data ? [{ id: 'behaviour', label: 'What happens at runtime' }] : []),
      ...(s?.constraints.length ? [{ id: 'rules', label: 'Rules to follow' }] : []),
      ...(s?.xmlExample ? [{ id: 'example', label: 'Example' }] : []),
    ];
  });

  protected readonly trail = computed(() => {
    const s = this.shape().data;
    return [
      { label: 'Argus Properties', link: '/shapes' },
      { label: 'Shapes', link: '/shapes' },
      ...(s ? [{ label: this.catalogue.labelOf(s.category), link: '/shapes', query: { category: s.category } }] : []),
      ...(s ? [{ label: s.name }] : []),
    ];
  });

  protected readonly sizeText = computed(() => {
    const notation = this.shape().data?.notation;
    if (!notation?.defaultWidth) return '—';
    const resize = notation.resizable ? ', and you can resize it' : ', fixed size';
    return `${notation.defaultWidth} × ${notation.defaultHeight}${resize}`;
  });

  /** Green for the happy path, red for the ways it silently goes wrong. */
  protected outcomeColour(outcome: Outcome): LozengeColour {
    switch (outcome.id) {
      case 'COMPLETED':
        return 'green';
      case 'WAITING':
        return 'blue';
      case 'STUCK':
      case 'INCIDENT':
        return 'red';
      case 'BPMN_ERROR':
        return 'yellow';
      default:
        return 'default';
    }
  }

  protected readable(value: string): string {
    return value.toLowerCase().replace(/_/g, ' ');
  }

  protected colourOf(category: string): LozengeColour {
    return CATEGORY_COLOUR[category] ?? 'default';
  }

  /**
   * Listeners are extension elements, not attributes: blocks of configuration inside
   * <bpmn:extensionElements> rather than a value on the tag. That is a different thing to look for,
   * so it gets its own filter instead of being spread through a list sorted by name.
   */
  protected readonly visibleProperties = computed<Property[]>(() => {
    const all = this.properties().data?.properties ?? [];
    return this.extensionsOnly() ? all.filter((p) => p.kind === 'EXTENSION_ELEMENT') : all;
  });

  protected toggleExtensions(): void {
    this.extensionsOnly.update((value) => !value);
  }

  protected toggleOwn(): void {
    this.own.update((value) => !value);
  }

  protected toggleNamespace(value: string): void {
    this.namespace.update((current) => (current === value ? undefined : value));
  }

  protected toggleClass(active: boolean): string {
    const base = 'rounded-[3px] border px-2.5 py-1 text-xs font-medium transition';
    return active
      ? `${base} border-link bg-link text-white`
      : `${base} border-doc-border text-ink-subtle hover:border-link hover:text-link dark:border-strokedark dark:text-bodydark`;
  }

  /** The service returns examples on one line; break them at element boundaries so they read. */
  protected formatXml(xml: string): string {
    let depth = 0;
    return xml
      .replace(/></g, '>\n<')
      .split('\n')
      .map((raw) => {
        const line = raw.trim();
        if (line.startsWith('</')) depth = Math.max(0, depth - 1);
        const rendered = '  '.repeat(depth) + line;
        if (line.startsWith('<') && !line.startsWith('</') && !line.endsWith('/>') && !line.includes('</')) depth += 1;
        return rendered;
      })
      .join('\n');
  }
}
