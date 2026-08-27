import { Injectable, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { debounceTime } from 'rxjs';

/**
 * The header's search box, shared with the shapes page.
 *
 * A signal in a root service rather than an input chain: the box lives in the header and the
 * results live in a routed page, so there is no parent-child relationship to pass it down.
 */
@Injectable({ providedIn: 'root' })
export class SearchStore {
  readonly term = signal('');

  /** What the shapes page actually queries on, so a keystroke is not a request. */
  readonly debounced = toSignal(toObservable(this.term).pipe(debounceTime(200)), { initialValue: '' });
}
