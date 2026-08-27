import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/*
 * Confluence page furniture.
 *
 * A dashboard answers "how are things right now" by putting everything on one screen. A
 * documentation page answers "how does this work" by putting one thing on the screen and letting
 * you read it top to bottom. These are the pieces of the second kind - panels, lozenges, a code
 * macro, a table of contents - the vocabulary anyone who has used Confluence already knows.
 *
 * Every variant is a complete literal class string in a lookup, never interpolated: Tailwind scans
 * source text, so a name built at runtime produces no CSS and renders unstyled.
 */

export type PanelKind = 'info' | 'note' | 'warning' | 'success' | 'error';

const PANEL_STYLE: Record<PanelKind, string> = {
  info: 'bg-panel-info/60 border-l-[3px] border-link dark:bg-link/10',
  note: 'bg-panel-note/60 border-l-[3px] border-[#6554c0] dark:bg-[#6554c0]/15',
  warning: 'bg-panel-warn/70 border-l-[3px] border-warning dark:bg-warning/10',
  success: 'bg-panel-success/70 border-l-[3px] border-success dark:bg-success/10',
  error: 'bg-panel-error/70 border-l-[3px] border-danger dark:bg-danger/10',
};

const PANEL_ICON: Record<PanelKind, string> = {
  info: 'ℹ',
  note: '✎',
  warning: '⚠',
  success: '✓',
  error: '✕',
};

@Component({
  selector: 'app-panel',
  template: `
    <div class="my-4 rounded-sm px-4 py-3" [class]="style()">
      <div class="flex gap-3">
        <span aria-hidden="true" class="mt-0.5 shrink-0 text-sm">{{ icon() }}</span>
        <div class="doc-prose min-w-0">
          @if (heading()) {
            <p class="mb-1 font-semibold text-ink dark:text-white">{{ heading() }}</p>
          }
          <ng-content />
        </div>
      </div>
    </div>
  `,
})
export class Panel {
  readonly kind = input<PanelKind>('info');
  readonly heading = input<string>();
  protected readonly style = () => PANEL_STYLE[this.kind()];
  protected readonly icon = () => PANEL_ICON[this.kind()];
}

export type LozengeColour = 'default' | 'blue' | 'green' | 'yellow' | 'red' | 'purple';

const LOZENGE_STYLE: Record<LozengeColour, string> = {
  default: 'bg-[#dfe1e6] text-[#42526e] dark:bg-meta-4 dark:text-bodydark1',
  blue: 'bg-[#deebff] text-[#0747a6] dark:bg-link/25 dark:text-secondary',
  green: 'bg-[#e3fcef] text-[#006644] dark:bg-success/25 dark:text-[#79f2c0]',
  yellow: 'bg-[#fff0b3] text-[#172b4d] dark:bg-warning/25 dark:text-warning',
  red: 'bg-[#ffebe6] text-[#bf2600] dark:bg-danger/25 dark:text-[#ff8f73]',
  purple: 'bg-[#eae6ff] text-[#403294] dark:bg-[#6554c0]/30 dark:text-[#c0b6f2]',
};

/** Confluence's status macro: a small uppercase pill. */
@Component({
  selector: 'app-lozenge',
  template: `
    <span
      class="inline-flex items-center rounded-[3px] px-1.5 py-0.5 text-[0.6875rem] font-bold tracking-wide uppercase"
      [class]="style()"
    >
      <ng-content />
    </span>
  `,
})
export class Lozenge {
  readonly colour = input<LozengeColour>('default');
  protected readonly style = () => LOZENGE_STYLE[this.colour()];
}

/** Confluence's code macro: a titled bar above a scrollable body. */
@Component({
  selector: 'app-code-macro',
  template: `
    <figure class="border-doc-border dark:border-strokedark my-4 overflow-hidden rounded-sm border">
      @if (heading()) {
        <figcaption
          class="border-doc-border bg-doc-sunken text-ink-subtle dark:border-strokedark dark:bg-boxdark-2 dark:text-bodydark2 border-b px-4 py-2 text-xs font-semibold"
        >
          {{ heading() }}
        </figcaption>
      }
      <pre
        class="text-ink dark:bg-boxdark-2 dark:text-bodydark1 overflow-x-auto bg-white px-4 py-3 font-mono text-[0.8125rem] leading-relaxed"
      ><code>{{ code() }}</code></pre>
    </figure>
  `,
})
export class CodeMacro {
  readonly heading = input<string>();
  readonly code = input.required<string>();
}

export interface TocEntry {
  id: string;
  label: string;
}

/** "On this page", sticky beside the content. */
@Component({
  selector: 'app-toc',
  template: `
    <nav class="sticky top-24 hidden w-56 shrink-0 xl:block" aria-label="On this page">
      <p class="text-ink-subtle dark:text-bodydark2 mb-3 text-xs font-bold tracking-wide uppercase">
        On this page
      </p>
      <ul class="border-doc-border dark:border-strokedark flex flex-col gap-2 border-l pl-4">
        @for (entry of entries(); track entry.id) {
          <li>
            <a
              [href]="'#' + entry.id"
              class="text-ink-subtle hover:text-link dark:text-bodydark text-sm hover:underline"
              >{{ entry.label }}</a
            >
          </li>
        }
      </ul>
    </nav>
  `,
})
export class TableOfContents {
  readonly entries = input.required<TocEntry[]>();
}

/** A page section with a linkable heading, so the table of contents has somewhere to go. */
@Component({
  selector: 'app-section',
  template: `
    <section [id]="anchor()" class="border-doc-border dark:border-strokedark scroll-mt-24 border-t pt-8">
      <h2 class="group text-ink text-xl font-semibold dark:text-white">
        <a [href]="'#' + anchor()" class="hover:text-link">
          {{ heading() }}
          <span class="ml-2 opacity-0 transition group-hover:opacity-40">#</span>
        </a>
      </h2>
      @if (lead()) {
        <p class="doc-prose doc-measure text-ink-subtle dark:text-bodydark mt-1.5">{{ lead() }}</p>
      }
      <div class="mt-4"><ng-content /></div>
    </section>
  `,
})
export class Section {
  readonly anchor = input.required<string>();
  readonly heading = input.required<string>();
  readonly lead = input<string>();
}

export interface Crumb {
  label: string;
  link?: string;
  query?: Record<string, string>;
}

@Component({
  selector: 'app-breadcrumbs',
  imports: [RouterLink],
  template: `
    <nav class="text-ink-subtle dark:text-bodydark2 mb-3 flex flex-wrap items-center gap-1.5 text-xs">
      @for (crumb of trail(); track crumb.label; let first = $first) {
        <span class="flex items-center gap-1.5">
          @if (!first) {
            <span aria-hidden="true">/</span>
          }
          @if (crumb.link) {
            <a [routerLink]="crumb.link" [queryParams]="crumb.query ?? {}" class="hover:text-link hover:underline">{{
              crumb.label
            }}</a>
          } @else {
            <span>{{ crumb.label }}</span>
          }
        </span>
      }
    </nav>
  `,
})
export class Breadcrumbs {
  readonly trail = input.required<Crumb[]>();
}

/** The page shell: title block, optional lead, content column and table of contents. */
@Component({
  selector: 'app-doc-page',
  imports: [TableOfContents],
  template: `
    <div class="flex gap-10">
      <article class="min-w-0 flex-1">
        <header class="pb-6">
          <h1 class="text-ink text-[1.75rem] leading-tight font-semibold dark:text-white">{{ heading() }}</h1>
          @if (lead()) {
            <p class="doc-prose doc-measure mt-3">{{ lead() }}</p>
          }
          <div class="mt-4 flex flex-wrap items-center gap-2 empty:mt-0">
            <ng-content select="[meta]" />
          </div>
        </header>
        <div class="flex flex-col gap-8"><ng-content /></div>
      </article>
      @if (toc().length) {
        <app-toc [entries]="toc()" />
      }
    </div>
  `,
})
export class DocPage {
  readonly heading = input.required<string>();
  readonly lead = input<string>();
  readonly toc = input<TocEntry[]>([]);
}
