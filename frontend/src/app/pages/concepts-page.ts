import { Component, computed, inject } from '@angular/core';
import { Api } from '../core/api';
import { asyncOnce } from '../core/async-state';
import { Breadcrumbs, CodeMacro, DocPage, Panel, Section } from '../ui/doc';
import { ErrorState, Spinner } from '../ui/states';

/**
 * The starting page: what a shape is, what a property is, in plain language.
 *
 * First in the tree because the rest of this site assumes the vocabulary. Someone who has never
 * opened a .bpmn file can read this top to bottom and then use the catalogue.
 */
@Component({
  selector: 'app-concepts-page',
  imports: [Breadcrumbs, CodeMacro, DocPage, Panel, Section, ErrorState, Spinner],
  template: `
    @let result = concepts();
    @if (result.error) {
      <app-error-state [message]="result.error" />
    } @else if (result.loading) {
      <app-spinner />
    } @else {
      @let entries = result.data ?? [];
      <app-breadcrumbs [trail]="[{ label: 'Argus Properties', link: '/shapes' }, { label: 'Concepts' }]" />
      <app-doc-page
        heading="Start here: the words used on this site"
        lead="A .bpmn file is a drawing and a configuration file at the same time, and the words for its parts come from three places at once — the BPMN standard, Camunda, and the modeller you draw in. This page explains the handful you need, with an example for each."
        [toc]="toc()"
      >
        <app-panel kind="info" heading="The short version">
          <p>
            You drag <strong>shapes</strong> onto a canvas. Each shape has <strong>properties</strong> you fill in.
            Some properties are standard BPMN and work in any tool; the ones starting
            <code class="font-mono">camunda:</code> only mean something to Camunda. Everything on this site is one of
            those two things.
          </p>
        </app-panel>

        @for (concept of entries; track concept.id) {
          <app-section [anchor]="concept.id" [heading]="concept.term" [lead]="concept.inShort">
            <p class="doc-prose doc-measure">{{ concept.explanation }}</p>
            <app-code-macro heading="For example" [code]="concept.example" />
            @if (concept.related.length) {
              <p class="text-ink-subtle dark:text-bodydark2 text-sm">
                See also
                @for (id of concept.related; track id; let last = $last) {
                  <a [href]="'#' + id" class="text-link hover:underline">{{ termOf(entries, id) }}</a
                  >{{ last ? '.' : ', ' }}
                }
              </p>
            }
          </app-section>
        }
      </app-doc-page>
    }
  `,
})
export class ConceptsPage {
  private readonly api = inject(Api);
  protected readonly concepts = asyncOnce(() => this.api.concepts());

  // Angular templates have no arrow functions by design - derived shapes belong in the class.
  protected readonly toc = computed(() =>
    (this.concepts().data ?? []).map((concept) => ({ id: concept.id, label: concept.term })),
  );

  protected termOf(entries: { id: string; term: string }[], id: string): string {
    return entries.find((entry) => entry.id === id)?.term ?? id;
  }
}
