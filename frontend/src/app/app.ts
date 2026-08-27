import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CatalogueStore } from './core/catalogue-store';
import { Header } from './layout/header';
import { Sidebar } from './layout/sidebar';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Header, Sidebar],
  template: `
    <div class="dark:bg-boxdark-2 flex h-screen overflow-hidden bg-white">
      <app-sidebar [categories]="catalogue.categories().data ?? []" [open]="sidebarOpen()" />

      <div class="relative flex flex-1 flex-col overflow-x-hidden overflow-y-auto">
        <app-header (toggleSidebar)="sidebarOpen.set(!sidebarOpen())" />
        <main class="mx-auto w-full max-w-6xl px-4 py-8 md:px-10 md:py-10">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class App {
  protected readonly sidebarOpen = signal(false);
  protected readonly catalogue = inject(CatalogueStore);
}
