import { Injectable, computed, inject } from '@angular/core';
import { Api } from './api';
import { asyncOnce } from './async-state';

/**
 * The category list, fetched once and shared.
 *
 * <p>Category wording used to be a lookup in the frontend as well as in the service, which is one
 * table too many: renaming a family meant editing both, and forgetting one produced a UI that
 * disagreed with its own API. The service now returns the label, and this holds the single copy
 * every page reads.
 */
@Injectable({ providedIn: 'root' })
export class CatalogueStore {
  private readonly api = inject(Api);

  readonly categories = asyncOnce(() => this.api.categories());

  private readonly labels = computed(
    () => new Map((this.categories().data ?? []).map((entry) => [entry.category, entry.label])),
  );

  /**
   * Falls back to title-casing the id, which only shows during the first paint before the
   * categories arrive - and is close enough that nothing visibly flickers.
   */
  labelOf(category: string): string {
    return (
      this.labels().get(category) ??
      category.charAt(0) + category.slice(1).toLowerCase().replace(/_/g, ' ')
    );
  }
}
