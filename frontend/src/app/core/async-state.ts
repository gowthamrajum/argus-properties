import { type Signal, untracked } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import type { HttpErrorResponse } from '@angular/common/http';
import { type Observable, catchError, map, of, startWith, switchMap } from 'rxjs';

export interface AsyncState<T> {
  data?: T;
  error?: string;
  loading: boolean;
}

/**
 * Turns a signal of request parameters into a signal of load state.
 *
 * <p>Deliberately built on `toSignal`/`toObservable` rather than `resource()`: `resource()` is the
 * more direct expression of this, but is still marked experimental in Angular 20, and an app's
 * data layer is the wrong place to take that bet. `switchMap` gives the same cancellation
 * guarantee - a slow response for old parameters cannot overwrite a newer one.
 */
export function asyncState<P, T>(
  params: Signal<P>,
  load: (params: P) => Observable<T>,
): Signal<AsyncState<T>> {
  const initial: AsyncState<T> = { loading: true };
  return toSignal(
    toObservable(params).pipe(
      switchMap((value) =>
        load(value).pipe(
          map((data): AsyncState<T> => ({ data, loading: false })),
          catchError((error: HttpErrorResponse) => of<AsyncState<T>>({ error: messageOf(error), loading: false })),
          startWith(initial),
        ),
      ),
    ),
    { initialValue: initial },
  );
}

/** Loads once, for the calls that take no parameters. */
export function asyncOnce<T>(load: () => Observable<T>): Signal<AsyncState<T>> {
  const initial: AsyncState<T> = { loading: true };
  return toSignal(
    untracked(() => load()).pipe(
      map((data): AsyncState<T> => ({ data, loading: false })),
      catchError((error: HttpErrorResponse) => of<AsyncState<T>>({ error: messageOf(error), loading: false })),
      startWith(initial),
    ),
    { initialValue: initial },
  );
}

/**
 * The service answers an unknown id with a 404 whose message names the endpoint that lists valid
 * ids. Surfacing that message rather than a generic failure is the point of its error shape.
 */
function messageOf(error: HttpErrorResponse): string {
  const body = error.error as { message?: string } | null;
  return body?.message ?? error.message ?? 'The request could not be completed.';
}
