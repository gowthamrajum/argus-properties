import { Component, input } from '@angular/core';

@Component({
  selector: 'app-spinner',
  template: `
    <div class="text-ink-subtle dark:text-bodydark2 flex items-center justify-center gap-3 py-16 text-sm">
      <span
        class="border-doc-border border-t-link dark:border-strokedark dark:border-t-secondary size-5 animate-spin rounded-full border-2"
      ></span>
      {{ label() }}
    </div>
  `,
})
export class Spinner {
  readonly label = input('Loading');
}

/** The service's 404s name the endpoint that lists valid ids, so the message is shown verbatim. */
@Component({
  selector: 'app-error-state',
  template: `
    <div class="border-danger bg-panel-error/70 dark:bg-danger/10 my-4 rounded-sm border-l-[3px] px-4 py-3">
      <p class="text-ink font-semibold dark:text-white">That did not work</p>
      <p class="text-ink-subtle dark:text-bodydark mt-1 text-sm">{{ message() }}</p>
      <p class="text-ink-subtle dark:text-bodydark2 mt-2 text-xs">
        Is argus-properties running on <code class="font-mono">:8081</code>?
      </p>
    </div>
  `,
})
export class ErrorState {
  readonly message = input.required<string>();
}

@Component({
  selector: 'app-empty-state',
  template: `
    <div class="border-doc-border dark:border-strokedark rounded-sm border border-dashed py-16 text-center">
      <p class="text-ink font-semibold dark:text-white">Nothing matches</p>
      <p class="text-ink-subtle dark:text-bodydark mt-1 text-sm">{{ message() }}</p>
    </div>
  `,
})
export class EmptyState {
  readonly message = input.required<string>();
}
