import { Component, inject, output, signal } from '@angular/core';
import { SearchStore } from '../core/search';

@Component({
  selector: 'app-header',
  template: `
    <header
      class="border-doc-border dark:border-strokedark dark:bg-boxdark sticky top-0 z-999 flex w-full border-b bg-white"
    >
      <div class="flex flex-grow items-center gap-4 px-4 py-3 md:px-8">
        <button
          type="button"
          aria-label="Toggle sidebar"
          (click)="toggleSidebar.emit()"
          class="border-doc-border dark:border-strokedark dark:bg-boxdark rounded-sm border bg-white p-1.5 lg:hidden"
        >
          <span class="bg-ink block h-0.5 w-5 dark:bg-white"></span>
          <span class="bg-ink mt-1 block h-0.5 w-5 dark:bg-white"></span>
          <span class="bg-ink mt-1 block h-0.5 w-5 dark:bg-white"></span>
        </button>

        <div class="relative w-full max-w-md">
          <input
            type="search"
            [value]="search.term()"
            (input)="onSearch($event)"
            placeholder="Search shapes — try “boundary”, or “bpmn:userTask”"
            class="border-doc-border bg-doc-sunken text-ink focus:border-link dark:border-strokedark dark:bg-form-input dark:focus:border-secondary w-full rounded-[3px] border px-3 py-2 text-sm outline-none focus:bg-white dark:text-white"
          />
        </div>

        <button
          type="button"
          (click)="toggleTheme()"
          aria-label="Toggle dark mode"
          class="border-doc-border text-ink hover:text-link dark:border-strokedark dark:bg-meta-4 ml-auto flex size-9 items-center justify-center rounded-[3px] border bg-white dark:text-white"
        >
          {{ dark() ? '☾' : '☀' }}
        </button>
      </div>
    </header>
  `,
})
export class Header {
  protected readonly search = inject(SearchStore);
  protected readonly dark = signal(document.documentElement.classList.contains('dark'));

  readonly toggleSidebar = output<void>();

  protected onSearch(event: Event): void {
    this.search.term.set((event.target as HTMLInputElement).value);
  }

  protected toggleTheme(): void {
    this.dark.update((value) => !value);
    document.documentElement.classList.toggle('dark', this.dark());
    localStorage.setItem('argus-theme', this.dark() ? 'dark' : 'light');
  }
}
