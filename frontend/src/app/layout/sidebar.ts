import { Component, computed, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, type Event as RouterEvent } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import type { CategoryEntry } from '../core/models';

const ITEM =
  'flex items-center gap-2 rounded-[3px] px-3 py-1.5 text-sm text-ink-subtle transition hover:bg-black/5 hover:text-ink dark:text-bodydark dark:hover:bg-white/5 dark:hover:text-white';
const ITEM_ACTIVE =
  'bg-link/10 font-semibold text-link hover:bg-link/10 hover:text-link dark:bg-link/20 dark:text-secondary';

/**
 * The page tree, in Confluence's idiom: light, quiet, and structural.
 *
 * A dark dashboard rail competes with the content for attention, which is right when the content
 * is charts and wrong when it is prose. Here the sidebar's only job is telling you where you are
 * and what else there is.
 */
@Component({
  selector: 'app-sidebar',
  imports: [RouterLink],
  template: `
    <aside
      class="border-doc-border bg-doc-sunken dark:border-strokedark dark:bg-boxdark absolute top-0 left-0 z-9999 flex h-screen w-72 flex-col overflow-y-auto border-r duration-300 ease-linear lg:static lg:translate-x-0"
      [class]="open() ? 'translate-x-0' : '-translate-x-full'"
    >
      <div class="border-doc-border dark:border-strokedark flex items-center gap-3 border-b px-5 py-4">
        <div class="bg-link flex size-8 items-center justify-center rounded-[3px] text-sm font-bold text-white">A</div>
        <div class="min-w-0">
          <p class="text-ink truncate text-sm font-semibold dark:text-white">Argus Properties</p>
          <p class="text-ink-subtle dark:text-bodydark2 truncate text-xs">BPMN reference</p>
        </div>
      </div>

      <nav class="flex flex-col gap-6 px-3 py-4">
        <ul class="flex flex-col gap-0.5">
          <li>
            <a routerLink="/concepts" [class]="itemClass(path() === '/concepts')">Start here</a>
          </li>
          <li>
            <a routerLink="/shapes" [class]="itemClass(path() === '/shapes' && !activeCategory())">
              All shapes
              <span class="text-ink-subtle dark:text-bodydark2 ml-auto text-xs">{{ total() }}</span>
            </a>
          </li>
          <li>
            <a routerLink="/properties" [class]="itemClass(path() === '/properties')">Property index</a>
          </li>
          <li>
            <a routerLink="/rules" [class]="itemClass(path() === '/rules')">
              Rules
              <span class="bg-link/15 text-link ml-auto rounded-[3px] px-1.5 text-[0.625rem] font-bold uppercase">edit</span>
            </a>
          </li>
          <li>
            <a routerLink="/listeners" [class]="itemClass(path() === '/listeners')">Listeners</a>
          </li>
          <li>
            <a routerLink="/event-shapes" [class]="itemClass(path() === '/event-shapes')">Event shapes</a>
          </li>
          <li>
            <a routerLink="/property-groups" [class]="itemClass(path() === '/property-groups')">
              Shared property groups
            </a>
          </li>
        </ul>

        <div>
          <p class="text-ink-subtle dark:text-bodydark2 mb-1.5 px-3 text-xs font-bold tracking-wide uppercase">
            Shapes by family
          </p>
          <ul class="flex flex-col gap-0.5">
            @for (entry of categories(); track entry.category) {
              <li>
                <a
                  routerLink="/shapes"
                  [queryParams]="{ category: entry.category }"
                  [class]="itemClass(path() === '/shapes' && activeCategory() === entry.category)"
                >
                  {{ entry.label }}
                  <span class="text-ink-subtle dark:text-bodydark2 ml-auto text-xs">{{ entry.shapeCount }}</span>
                </a>
              </li>
            }
          </ul>
        </div>
      </nav>
    </aside>
  `,
})
export class Sidebar {
  readonly categories = input.required<CategoryEntry[]>();
  readonly open = input(false);

  private readonly router = inject(Router);

  /**
   * routerLinkActive cannot express "active only when this query parameter matches", and every
   * category link points at the same path. So the URL is read directly instead.
   */
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event: RouterEvent): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  protected readonly path = computed(() => this.url().split('?')[0]);
  protected readonly activeCategory = computed(
    () => new URLSearchParams(this.url().split('?')[1] ?? '').get('category'),
  );
  protected readonly total = computed(() => this.categories().reduce((sum, e) => sum + e.shapeCount, 0));

  protected itemClass(active: boolean): string {
    return active ? `${ITEM} ${ITEM_ACTIVE}` : ITEM;
  }

}
