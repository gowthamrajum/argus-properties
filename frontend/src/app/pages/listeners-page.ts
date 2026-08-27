import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { asyncOnce } from '../core/async-state';
import { Breadcrumbs, DocPage, Lozenge, Panel, Section } from '../ui/doc';
import { ErrorState, Spinner } from '../ui/states';

/**
 * Listener events, which are the actual decision.
 *
 * <p>The property catalogue can say a user task accepts camunda:taskListener and still leave the
 * only thing that matters unanswered: which event. Six values behave differently enough that the
 * wrong one produces a listener that either never fires or fires at a moment when the thing it
 * wants to read does not exist yet - and neither failure looks like a failure.
 */
@Component({
  selector: 'app-listeners-page',
  imports: [RouterLink, Breadcrumbs, DocPage, Lozenge, Panel, Section, ErrorState, Spinner],
  template: `
    @let result = listeners();
    @if (result.error) {
      <app-error-state [message]="result.error" />
    } @else if (result.loading) {
      <app-spinner />
    } @else {
      <app-breadcrumbs [trail]="[{ label: 'Argus Properties', link: '/shapes' }, { label: 'Listeners' }]" />
      <app-doc-page
        heading="Listeners, and when each one fires"
        lead="Listeners let you run code as a token moves or a task changes state, without adding a shape to the diagram. Choosing one is really choosing an event — and the events are not interchangeable."
        [toc]="toc()"
      >
        <app-panel kind="warning" heading="A listener is invisible on the diagram">
          <p>
            Nothing in the picture shows that one is attached. Behaviour a reader of the model needs
            to know about is usually better as a shape they can see.
          </p>
        </app-panel>

        @for (type of result.data ?? []; track type.id) {
          <app-section [anchor]="type.id" [heading]="type.name" [lead]="type.inShort">
            <p class="text-ink-subtle dark:text-bodydark mb-4 text-sm">
              <code class="font-mono">{{ type.tag }}</code> — {{ type.appliesTo }}
            </p>

            <div class="border-doc-border dark:border-strokedark overflow-x-auto rounded-sm border">
              <table class="w-full border-collapse text-left">
                <thead>
                  <tr class="bg-doc-sunken dark:bg-boxdark-2">
                    @for (column of ['Event', 'When it fires', 'What it is for']; track column) {
                      <th class="text-ink-subtle dark:text-bodydark2 px-4 py-2.5 text-xs font-bold tracking-wide uppercase">
                        {{ column }}
                      </th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (event of type.events; track event.event) {
                    <tr class="border-doc-border dark:border-strokedark border-t align-top">
                      <td class="px-4 py-3 whitespace-nowrap">
                        <code class="text-ink font-mono text-sm font-semibold dark:text-white">{{ event.event }}</code>
                        @if (event.validOn.length === 1) {
                          <div class="mt-1.5">
                            <app-lozenge colour="purple">{{ event.validOn[0] }} only</app-lozenge>
                          </div>
                        }
                      </td>
                      <td class="text-ink dark:text-bodydark1 max-w-lg px-4 py-3 text-sm">
                        {{ event.firesWhen }}
                        @if (event.caveat) {
                          <p class="border-warning text-ink-subtle dark:text-bodydark mt-2 border-l-2 pl-3 text-xs">
                            {{ event.caveat }}
                          </p>
                        }
                      </td>
                      <td class="text-ink-subtle dark:text-bodydark max-w-xs px-4 py-3 text-sm">{{ event.useFor }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>

            <h3 class="text-ink-subtle dark:text-bodydark2 mt-6 mb-2 text-sm font-bold tracking-wide uppercase">
              How to point it at code
            </h3>
            <ul class="flex flex-col gap-1.5">
              @for (option of type.implementations; track option) {
                <li class="border-doc-border text-ink-subtle dark:border-strokedark dark:text-bodydark border-l-2 pl-3 text-sm">
                  {{ option }}
                </li>
              }
            </ul>

            @for (note of type.notes; track note) {
              <app-panel kind="info">{{ note }}</app-panel>
            }

            <p class="text-ink-subtle dark:text-bodydark2 mt-4 text-sm">
              See every shape that accepts this:
              <a [routerLink]="['/properties']" class="text-link hover:underline">the property index</a>.
            </p>
          </app-section>
        }
      </app-doc-page>
    }
  `,
})
export class ListenersPage {
  private readonly api = inject(Api);
  protected readonly listeners = asyncOnce(() => this.api.listeners());

  protected readonly toc = computed(() =>
    (this.listeners().data ?? []).map((type) => ({ id: type.id, label: type.name })),
  );
}
