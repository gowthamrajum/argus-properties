import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import type { HttpErrorResponse } from '@angular/common/http';
import { Api } from '../core/api';
import { CatalogueStore } from '../core/catalogue-store';
import type { Rule, RuleKind, RuleQuery, Severity } from '../core/models';
import { Breadcrumbs, DocPage, Lozenge, Panel, type LozengeColour } from '../ui/doc';
import { EmptyState, ErrorState, Spinner } from '../ui/states';

const SEVERITY_COLOUR: Record<Severity, LozengeColour> = {
  HIGH: 'red',
  MEDIUM: 'yellow',
  LOW: 'default',
};

/**
 * The one screen in this app that writes.
 *
 * <p>Everything else documents BPMN, which is true regardless of who is asking. A rule encodes what
 * one team has decided, so it is authored here rather than declared in Java and released - and that
 * makes this the only place a mistake can be saved rather than merely displayed. Hence the
 * confirmation on delete, the server's own validation messages surfaced verbatim, and enabling
 * rather than deleting being offered first.
 */
@Component({
  selector: 'app-rules-page',
  imports: [ReactiveFormsModule, RouterLink, Breadcrumbs, DocPage, Lozenge, Panel, EmptyState, ErrorState, Spinner],
  template: `
    <app-breadcrumbs [trail]="[{ label: 'Argus Properties', link: '/shapes' }, { label: 'Rules' }]" />
    <app-doc-page
      heading="Rules"
      lead="What your team has decided a model must do, written against the shapes it applies to. Unlike everything else on this site, these are yours to edit — the catalogue describes BPMN, rules describe your standards."
    >
      <ng-container meta>
        <app-lozenge colour="blue">{{ rules().length }} rules</app-lozenge>
        <app-lozenge colour="red">{{ countOf('VIOLATION') }} violations</app-lozenge>
        <app-lozenge colour="yellow">{{ countOf('FINDING') }} findings</app-lozenge>
      </ng-container>

      <app-panel kind="info" heading="Violations and findings">
        <p>
          A <strong>violation</strong> breaches a standard you have committed to; a
          <strong>finding</strong> is modelling hygiene — legal, but ill-advised. They are stored
          together and differ only in what you call the result, so a rule can be reclassified without
          being rewritten.
        </p>
      </app-panel>

      @if (error(); as message) {
        <app-error-state [message]="message" />
      }

      <!-- Filters -->
      <div class="flex flex-wrap items-center gap-2">
        <span class="text-ink-subtle dark:text-bodydark2 text-xs font-semibold tracking-wide uppercase">Show</span>
        @for (option of KINDS; track option.label) {
          <button type="button" (click)="setKind(option.value)" [class]="chip(kind() === option.value)">
            {{ option.label }}
          </button>
        }
        <span class="border-doc-border dark:border-strokedark mx-1 h-5 border-l"></span>
        @for (option of SEVERITIES; track option.label) {
          <button type="button" (click)="setSeverity(option.value)" [class]="chip(severity() === option.value)">
            {{ option.label }}
          </button>
        }
        <button type="button" (click)="startCreate()" class="bg-link ml-auto rounded-[3px] px-3 py-1.5 text-xs font-semibold text-white">
          Author a rule
        </button>
      </div>

      <!-- Editor -->
      @if (editing()) {
        <form
          [formGroup]="form"
          (ngSubmit)="save()"
          class="border-link/40 bg-doc-sunken dark:bg-boxdark-2 rounded-sm border p-5"
        >
          <h3 class="text-ink mb-4 font-semibold dark:text-white">
            {{ editingId() ? 'Edit rule' : 'Author a rule' }}
          </h3>

          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <label class="flex flex-col gap-1">
              <span class="text-ink-subtle dark:text-bodydark2 text-xs font-semibold">Shape</span>
              <select formControlName="shapeId" [class]="field()">
                @for (entry of shapeOptions(); track entry.id) {
                  <option [value]="entry.id">{{ entry.name }}</option>
                }
              </select>
              @if (editingId()) {
                <span class="text-ink-subtle text-xs">Shape and code are the rule's identity and cannot change.</span>
              }
            </label>

            <label class="flex flex-col gap-1">
              <span class="text-ink-subtle dark:text-bodydark2 text-xs font-semibold">Code</span>
              <input formControlName="code" placeholder="USER_TASK_NO_ASSIGNMENT" [class]="field()" />
              <span class="text-ink-subtle text-xs">SCREAMING_SNAKE_CASE, unique within the shape.</span>
            </label>

            <label class="flex flex-col gap-1">
              <span class="text-ink-subtle dark:text-bodydark2 text-xs font-semibold">Kind</span>
              <select formControlName="kind" [class]="field()">
                <option value="VIOLATION">Violation — breaches a standard</option>
                <option value="FINDING">Finding — hygiene, legal but ill-advised</option>
              </select>
            </label>

            <label class="flex flex-col gap-1">
              <span class="text-ink-subtle dark:text-bodydark2 text-xs font-semibold">Severity</span>
              <select formControlName="severity" [class]="field()">
                <option value="HIGH">High — breaks deployment, or fails normally</option>
                <option value="MEDIUM">Medium — fails in plausible conditions</option>
                <option value="LOW">Low — works, but fragile or unclear</option>
              </select>
            </label>

            <label class="flex flex-col gap-1 sm:col-span-2">
              <span class="text-ink-subtle dark:text-bodydark2 text-xs font-semibold">Title</span>
              <input formControlName="title" placeholder="User task assigned to nobody" [class]="field()" />
            </label>

            <label class="flex flex-col gap-1 sm:col-span-2">
              <span class="text-ink-subtle dark:text-bodydark2 text-xs font-semibold">Why it matters</span>
              <textarea formControlName="rationale" rows="3" [class]="field()"></textarea>
              <span class="text-ink-subtle text-xs">The reasoning, not the instance. This is what makes a rule arguable rather than arbitrary.</span>
            </label>

            <label class="flex flex-col gap-1 sm:col-span-2">
              <span class="text-ink-subtle dark:text-bodydark2 text-xs font-semibold">What to change</span>
              <textarea formControlName="remediation" rows="2" [class]="field()"></textarea>
            </label>

            <label class="flex items-center gap-2 sm:col-span-2">
              <input type="checkbox" formControlName="enabled" class="accent-link size-4" />
              <span class="text-ink dark:text-bodydark1 text-sm">Enabled</span>
            </label>
          </div>

          <div class="mt-5 flex items-center gap-2">
            <button
              type="submit"
              [disabled]="form.invalid || saving()"
              class="bg-link rounded-[3px] px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-40"
            >
              {{ saving() ? 'Saving…' : editingId() ? 'Save changes' : 'Create rule' }}
            </button>
            <button type="button" (click)="cancel()" [class]="chip(false)">Cancel</button>
            @if (form.invalid && form.touched) {
              <span class="text-danger text-xs">Every field is required — including why it matters.</span>
            }
          </div>
        </form>
      }

      @if (loading()) {
        <app-spinner />
      } @else if (!rules().length) {
        <app-empty-state message="No rules match. Author one, or clear the filters." />
      } @else {
        <div class="border-doc-border dark:border-strokedark overflow-x-auto rounded-sm border">
          <table class="w-full border-collapse text-left">
            <thead>
              <tr class="bg-doc-sunken dark:bg-boxdark-2">
                @for (column of ['Rule', 'Shape', 'Severity', '']; track column) {
                  <th class="text-ink-subtle dark:text-bodydark2 px-4 py-2.5 text-xs font-bold tracking-wide uppercase">
                    {{ column }}
                  </th>
                }
              </tr>
            </thead>
            <tbody>
              @for (rule of rules(); track rule.id) {
                <tr class="border-doc-border dark:border-strokedark border-t align-top" [class.opacity-50]="!rule.enabled">
                  <td class="px-4 py-3">
                    <div class="flex flex-wrap items-center gap-2">
                      <span class="text-ink font-semibold dark:text-white">{{ rule.title }}</span>
                      <app-lozenge [colour]="rule.kind === 'VIOLATION' ? 'red' : 'yellow'">{{
                        rule.kind === 'VIOLATION' ? 'violation' : 'finding'
                      }}</app-lozenge>
                      @if (!rule.enabled) {
                        <app-lozenge>disabled</app-lozenge>
                      }
                    </div>
                    <code class="text-ink-subtle dark:text-bodydark2 mt-1 block font-mono text-xs">{{ rule.code }}</code>
                    <p class="text-ink-subtle dark:text-bodydark mt-1.5 max-w-2xl text-sm">{{ rule.rationale }}</p>
                    <p class="text-ink-subtle dark:text-bodydark mt-1 max-w-2xl text-sm">
                      <strong>Fix:</strong> {{ rule.remediation }}
                    </p>
                  </td>
                  <td class="px-4 py-3 whitespace-nowrap">
                    <a [routerLink]="['/shapes', rule.shapeId]" class="text-link text-sm hover:underline">{{
                      rule.shapeName
                    }}</a>
                  </td>
                  <td class="px-4 py-3">
                    <app-lozenge [colour]="severityColour(rule.severity)">{{ rule.severity }}</app-lozenge>
                  </td>
                  <td class="px-4 py-3 whitespace-nowrap">
                    <button type="button" (click)="startEdit(rule)" [class]="chip(false)">Edit</button>
                    @if (confirmingDelete() === rule.id) {
                      <button type="button" (click)="remove(rule)" class="bg-danger ml-1 rounded-[3px] px-2.5 py-1 text-xs font-semibold text-white">
                        Really delete?
                      </button>
                    } @else {
                      <button type="button" (click)="confirmingDelete.set(rule.id)" [class]="chip(false)">Delete</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </app-doc-page>
  `,
})
export class RulesPage {
  private readonly api = inject(Api);
  private readonly fb = inject(FormBuilder);
  protected readonly catalogue = inject(CatalogueStore);

  protected readonly KINDS = [
    { label: 'All kinds', value: undefined },
    { label: 'Violations', value: 'VIOLATION' as const },
    { label: 'Findings', value: 'FINDING' as const },
  ];
  protected readonly SEVERITIES = [
    { label: 'Any severity', value: undefined },
    { label: 'High', value: 'HIGH' as const },
    { label: 'Medium', value: 'MEDIUM' as const },
    { label: 'Low', value: 'LOW' as const },
  ];

  protected readonly rules = signal<Rule[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | undefined>(undefined);
  protected readonly kind = signal<RuleKind | undefined>(undefined);
  protected readonly severity = signal<Severity | undefined>(undefined);
  protected readonly editing = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly confirmingDelete = signal<number | null>(null);

  protected readonly shapeOptions = computed(() =>
    (this.catalogue.categories().data ?? []).flatMap((entry) =>
      entry.shapeIds.map((id) => ({ id, name: `${entry.label} · ${id}` })),
    ),
  );

  protected readonly form = this.fb.nonNullable.group({
    shapeId: ['user-task', Validators.required],
    code: ['', [Validators.required, Validators.pattern(/^[A-Z][A-Z0-9_]*$/)]],
    kind: ['VIOLATION' as RuleKind, Validators.required],
    severity: ['HIGH' as Severity, Validators.required],
    title: ['', Validators.required],
    rationale: ['', Validators.required],
    remediation: ['', Validators.required],
    enabled: [true],
  });

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    const query: RuleQuery = { kind: this.kind(), severity: this.severity() };
    this.api.rules(query).subscribe({
      next: (rules) => {
        this.rules.set(rules);
        this.loading.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(this.messageOf(e));
        this.loading.set(false);
      },
    });
  }

  protected countOf(kind: RuleKind): number {
    return this.rules().filter((rule) => rule.kind === kind).length;
  }

  protected setKind(value: RuleKind | undefined): void {
    this.kind.set(value);
    this.load();
  }

  protected setSeverity(value: Severity | undefined): void {
    this.severity.set(value);
    this.load();
  }

  protected startCreate(): void {
    this.editingId.set(null);
    this.form.reset({ shapeId: 'user-task', kind: 'VIOLATION', severity: 'HIGH', enabled: true, code: '', title: '', rationale: '', remediation: '' });
    this.form.controls.shapeId.enable();
    this.form.controls.code.enable();
    this.editing.set(true);
  }

  protected startEdit(rule: Rule): void {
    this.editingId.set(rule.id);
    this.form.setValue({
      shapeId: rule.shapeId,
      code: rule.code,
      kind: rule.kind,
      severity: rule.severity,
      title: rule.title,
      rationale: rule.rationale,
      remediation: rule.remediation,
      enabled: rule.enabled,
    });
    // The server rejects a change of identity; disabling the inputs says so before the round trip.
    this.form.controls.shapeId.disable();
    this.form.controls.code.disable();
    this.editing.set(true);
  }

  protected cancel(): void {
    this.editing.set(false);
    this.error.set(undefined);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(undefined);
    const request = this.form.getRawValue();
    const id = this.editingId();
    const done = {
      next: () => {
        this.saving.set(false);
        this.editing.set(false);
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.messageOf(e));
      },
    };
    if (id === null) {
      this.api.createRule(request).subscribe(done);
    } else {
      this.api.updateRule(id, request).subscribe(done);
    }
  }

  protected remove(rule: Rule): void {
    this.confirmingDelete.set(null);
    this.api.deleteRule(rule.id).subscribe({
      next: () => this.load(),
      error: (e: HttpErrorResponse) => this.error.set(this.messageOf(e)),
    });
  }

  protected severityColour(severity: Severity): LozengeColour {
    return SEVERITY_COLOUR[severity];
  }

  /** The server's messages name what is wrong and what to do, so they are shown verbatim. */
  private messageOf(error: HttpErrorResponse): string {
    return (error.error as { message?: string } | null)?.message ?? error.message;
  }

  protected field(): string {
    return 'rounded-[3px] border border-doc-border bg-white px-3 py-2 text-sm text-ink outline-none focus:border-link dark:border-strokedark dark:bg-form-input dark:text-white disabled:opacity-60';
  }

  protected chip(active: boolean): string {
    const base = 'rounded-[3px] border px-2.5 py-1 text-xs font-medium transition';
    return active
      ? `${base} border-link bg-link text-white`
      : `${base} border-doc-border text-ink-subtle hover:border-link hover:text-link dark:border-strokedark dark:text-bodydark`;
  }
}
