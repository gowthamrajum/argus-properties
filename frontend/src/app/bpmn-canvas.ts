import { HttpClient, type HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  type ElementRef,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import BpmnViewer from 'bpmn-js/lib/Viewer';

/**
 * bpmn-js types `get()` as returning unknown, so the one module this component touches is declared
 * locally. A narrow local shape beats importing diagram-js internals, which are not part of
 * bpmn-js's public surface and move between versions.
 */
interface DiagramCanvas {
  zoom(mode: 'fit-viewport', center: 'auto'): void;
}

/**
 * Renders a shape exactly the way bpmn.io does, by using bpmn.io.
 *
 * <p>The service returns a real .bpmn document containing just this shape, and this hands it
 * straight to bpmn-js. Nothing here knows what a gateway looks like - which is the point. An
 * approximation drawn by hand drifts from the real renderer the moment either changes, and drifts
 * silently, because a diamond that is subtly wrong still looks like a diamond.
 *
 * <p>The canvas stays white in dark mode on purpose: bpmn-js draws dark strokes on the assumption
 * of a light canvas, and a diagram is a figure - Confluence keeps images on their own background
 * rather than inverting them.
 */
@Component({
  selector: 'app-bpmn-canvas',
  template: `
    <figure class="border-doc-border dark:border-strokedark overflow-hidden rounded-sm border bg-white">
      <div class="relative w-full" [style.height.px]="height()">
        <div #host class="h-full w-full"></div>
        @if (status() !== 'ready') {
          <div class="text-ink-subtle absolute inset-0 flex items-center justify-center px-6 text-center text-sm">
            {{ status() === 'loading' ? 'Rendering…' : message() }}
          </div>
        }
      </div>
      @if (caption()) {
        <figcaption
          class="border-doc-border bg-doc-sunken text-ink-subtle dark:border-strokedark dark:bg-boxdark-2 dark:text-bodydark2 border-t px-4 py-2 text-xs"
        >
          {{ caption() }}
        </figcaption>
      }
    </figure>
  `,
})
export class BpmnCanvas {
  readonly shapeId = input.required<string>();
  readonly height = input(220);
  readonly caption = input<string>();

  private readonly http = inject(HttpClient);
  private readonly host = viewChild.required<ElementRef<HTMLDivElement>>('host');

  protected readonly status = signal<'loading' | 'ready' | 'unavailable'>('loading');
  protected readonly message = signal('');

  private viewer?: BpmnViewer;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.viewer?.destroy());

    effect((onCleanup) => {
      const id = this.shapeId();
      const container = this.host().nativeElement;
      let stale = false;
      onCleanup(() => {
        stale = true;
      });

      this.status.set('loading');
      // responseType text: the endpoint answers with BPMN XML, not JSON.
      this.http.get(`/api/v1/shapes/${id}/preview`, { responseType: 'text' }).subscribe({
        next: (xml) => {
          if (stale) return;
          void this.render(container, xml);
        },
        error: (error: HttpErrorResponse) => {
          if (stale) return;
          const body = safeJson(error.error);
          this.message.set(body?.message ?? 'This shape has no preview.');
          this.status.set('unavailable');
        },
      });
    });
  }

  private async render(container: HTMLDivElement, xml: string): Promise<void> {
    this.viewer ??= new BpmnViewer({ container });
    try {
      await this.viewer.importXML(xml);
      // 'fit-viewport' with centring, so a 36x36 event and a 400x160 pool both fill the frame.
      (this.viewer.get('canvas') as DiagramCanvas).zoom('fit-viewport', 'auto');
      this.status.set('ready');
    } catch {
      this.message.set('That diagram could not be rendered.');
      this.status.set('unavailable');
    }
  }
}

/** The error body is XML-typed on this endpoint, so the JSON error shape arrives as a string. */
function safeJson(value: unknown): { message?: string } | null {
  if (typeof value !== 'string') return (value as { message?: string } | null) ?? null;
  try {
    return JSON.parse(value) as { message?: string };
  } catch {
    return null;
  }
}
