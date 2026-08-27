import { Component, computed, inject } from '@angular/core';
import { Api } from '../core/api';
import { asyncOnce } from '../core/async-state';
import type { EventShape } from '../core/models';
import { Breadcrumbs, CodeMacro, DocPage, Lozenge, Panel, Section } from '../ui/doc';
import { ErrorState, Spinner } from '../ui/states';

const CONTEXT_LABEL: Record<string, string> = {
  ANY: 'anywhere',
  TOP_LEVEL: 'top level only',
  EVENT_SUB_PROCESS: 'event sub-process only',
};

/**
 * The concrete event shapes: five tags, roughly fifty things you can actually place.
 *
 * A palette does not offer "boundary event" - it offers "timer boundary event", and separately a
 * non-interrupting one. Those are a position crossed with a definition crossed with a flag, and
 * this page enumerates the combinations that are legal rather than leaving you to work out which
 * of the theoretical ones deploy.
 */
@Component({
  selector: 'app-event-shapes-page',
  imports: [Breadcrumbs, CodeMacro, DocPage, Lozenge, Panel, Section, ErrorState, Spinner],
  template: `
    @let result = eventShapes();
    @if (result.error) {
      <app-error-state [message]="result.error" />
    } @else if (result.loading) {
      <app-spinner />
    } @else {
      <app-breadcrumbs [trail]="[{ label: 'Argus Properties', link: '/shapes' }, { label: 'Event shapes' }]" />
      <app-doc-page
        heading="Every event shape you can place"
        lead="There are only five event tags in BPMN, but a modeller's palette offers around fifty event shapes. That is because a concrete shape is a position crossed with an event definition — and sometimes a flag on top. This is the list of combinations that are actually legal."
        [toc]="toc()"
      >
        <ng-container meta>
          <app-lozenge colour="blue">{{ (result.data ?? []).length }} shapes</app-lozenge>
          <app-lozenge>5 tags</app-lozenge>
        </ng-container>

        <app-panel kind="info" heading="How to read this">
          <p>
            <strong>Position</strong> is the tag — where the event sits in the flow. <strong>Definition</strong> is the
            child element that gives it its icon and meaning. <strong>Interrupting</strong> says whether firing cancels
            the thing it is attached to, or spawns a parallel token and leaves it running.
          </p>
        </app-panel>

        @for (group of grouped(); track group.position) {
          <app-section [anchor]="group.position" [heading]="group.label" [lead]="group.tag">
            <div class="border-doc-border dark:border-strokedark overflow-x-auto rounded-sm border">
              <table class="w-full border-collapse text-left">
                <thead>
                  <tr class="bg-doc-sunken dark:bg-boxdark-2">
                    @for (column of ['Shape', 'Definition', 'Where it is allowed']; track column) {
                      <th
                        class="text-ink-subtle dark:text-bodydark2 px-4 py-2.5 text-xs font-bold tracking-wide uppercase"
                      >
                        {{ column }}
                      </th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (shape of group.shapes; track shape.id) {
                    <tr class="border-doc-border dark:border-strokedark border-t align-top">
                      <td class="px-4 py-3">
                        <span class="text-ink font-semibold dark:text-white">{{ shape.name }}</span>
                        @if (shape.interrupting === false) {
                          <span class="ml-2 inline-block"><app-lozenge colour="yellow">non-interrupting</app-lozenge></span>
                        }
                        <p class="text-ink-subtle dark:text-bodydark mt-1 max-w-xl text-sm">{{ shape.summary }}</p>
                        @for (rule of shape.requires; track rule) {
                          <p class="border-warning text-ink-subtle dark:text-bodydark mt-2 border-l-2 pl-3 text-xs">
                            {{ rule }}
                          </p>
                        }
                      </td>
                      <td class="px-4 py-3">
                        @if (shape.definitionTag) {
                          <code class="text-ink-subtle dark:text-bodydark2 font-mono text-xs">{{
                            shape.definitionTag
                          }}</code>
                        } @else {
                          <span class="text-ink-subtle text-xs">none</span>
                        }
                      </td>
                      <td class="px-4 py-3 whitespace-nowrap">
                        <app-lozenge [colour]="shape.context === 'ANY' ? 'green' : 'purple'">{{
                          contextLabel(shape.context)
                        }}</app-lozenge>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <app-code-macro heading="How the first one is written" [code]="group.shapes[0].xmlSketch" />
          </app-section>
        }
      </app-doc-page>
    }
  `,
})
export class EventShapesPage {
  private readonly api = inject(Api);
  protected readonly eventShapes = asyncOnce(() => this.api.eventShapes());

  /** Grouped by position, because that is the axis with only five values. */
  protected readonly grouped = computed(() => {
    const byPosition = new Map<string, EventShape[]>();
    for (const shape of this.eventShapes().data ?? []) {
      const list = byPosition.get(shape.positionShapeId) ?? [];
      list.push(shape);
      byPosition.set(shape.positionShapeId, list);
    }
    return [...byPosition.entries()].map(([position, shapes]) => ({
      position,
      label: position.replace(/-/g, ' ').replace(/^./, (c) => c.toUpperCase()) + 's',
      tag: shapes[0].positionTag,
      shapes,
    }));
  });

  protected readonly toc = computed(() =>
    this.grouped().map((group) => ({ id: group.position, label: group.label })),
  );

  protected contextLabel(context: string): string {
    return CONTEXT_LABEL[context] ?? context;
  }
}
