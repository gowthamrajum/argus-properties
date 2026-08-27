# Argus Properties (Angular)

Angular frontend for [`argus-properties`](../argus-properties) — browse every BPMN shape a Camunda 7
/ Fluxnova file can contain, and every property each one carries.

Confluence-style documentation: page tree, breadcrumbs, "on this page" contents, info/warning
panels, status lozenges and code macros. Tailwind 4 with TailAdmin tokens for the app chrome, and
**Anek Telugu** as the type family.

## Run

The UI is a pure client of the service, so the service must be up first:

```bash
cd ../argus-properties && mvn spring-boot:run   # :8081
cd ../argus-properties-ng && npm install && npm start   # :4200
```

Then open <http://localhost:4200>. `proxy.conf.json` forwards `/api` to `:8081`, which keeps the app
same-origin in dev — so there is no CORS configuration on the service to keep in sync.

```bash
npm run build       # ng build -> dist/
npm run typecheck
```

## Stack

| Piece | Choice |
| --- | --- |
| Framework | Angular 20, standalone components, **zoneless** |
| Reactivity | Signals throughout — `signal`, `computed`, `input()`, `toSignal` |
| Routing | Angular Router with `withComponentInputBinding()`, lazy-loaded pages |
| HTTP | `HttpClient` with `withFetch()` |
| Styling | Tailwind CSS 4 via `@tailwindcss/postcss` |
| Font | Anek Telugu (Google Fonts, 100–800) |

## Screens

| Route | What it shows |
| --- | --- |
| `/concepts` | **Start here** — what a shape is, what a property is, in plain language with examples |
| `/shapes` | The catalogue index — filter by family, free text, or whether the engine runs it |
| `/shapes/:id` | One shape as a doc page: what it is · what you can set · how it is drawn · rules · example |
| `/event-shapes` | The ~49 concrete event shapes — position × definition × interrupting |
| `/property-groups` | The shared property sets shapes inherit, each with its full table |

`/` redirects to `/concepts`, because the rest of the site assumes that vocabulary. Every page is a
`loadComponent` route, so only the shell is in the initial bundle.

## How this differs from the React version

Both apps render the same documentation from the same service. The interesting differences are
where Angular's model wanted something genuinely different, not where the syntax differs:

**Query params are inputs, not hooks.** `withComponentInputBinding()` binds route and query
parameters straight to signal inputs, so a page declares what it filters on and never touches
`ActivatedRoute`:

```ts
readonly category = input<string>();     // ?category=GATEWAY
readonly executable = input<string>();   // ?executable=false
```

**No `useApi` hook — a signal-to-signal helper instead.** [`asyncState`](src/app/core/async-state.ts)
takes a signal of request parameters and returns a signal of `{ data, error, loading }`, with
`switchMap` giving the same cancellation guarantee the React hook's `cancelled` flag did:

```ts
private readonly query = computed(() => ({ id: this.id(), own: this.own(), namespace: this.namespace() }));
protected readonly properties = asyncState(this.query, (q) => this.api.properties(q.id, q));
```

It is built on `toSignal`/`toObservable` rather than `resource()`. `resource()` expresses this more
directly, but is still marked `@experimental` in Angular 20 — an app's data layer is the wrong place
to take that bet.

**Zoneless.** Every async value in the app is already a signal, so there is nothing for zone.js to
patch. `provideZonelessChangeDetection()`, and `polyfills` in `angular.json` is empty.

**Search is a root service, not prop drilling.** The box lives in the header and the results live in
a routed page, so there is no parent-child relationship to pass it down —
[`SearchStore`](src/app/core/search.ts) holds the signal and the debounce next to it.

**Derived values live in the class.** Angular templates have no arrow functions by design, so the
tables of contents and breadcrumb trails are `computed()` members rather than inline `.map()` calls.
This is stricter than JSX and, for anything non-trivial, easier to read.

## Two Tailwind gotchas this codebase avoids

Tailwind scans source *text*, so a class name built at runtime produces no CSS — the class lands in
the DOM and does nothing. Every variant is a complete literal string in a lookup:

```ts
const LOZENGE_STYLE: Record<LozengeColour, string> = {
  green: 'bg-[#e3fcef] text-[#006644] dark:bg-success/25',   // ✅ scannable
};
// `bg-${colour}-500`                                         // ❌ silently unstyled
```

Inline templates in `.ts` files are scanned the same way as `.html`, so nothing extra is needed —
but it does mean a class only mentioned in a comment will be generated.

## Layout

```
src/app/
  core/       models.ts · api.ts · async-state.ts · search.ts
  ui/         doc.ts (panel, lozenge, code macro, section, breadcrumbs, ToC, page shell) · states.ts
  layout/     sidebar.ts · header.ts
  pages/      concepts · shapes · shape-detail · property-groups
  shape-preview.ts   SVG drawn from the shape's own notation data
  property-table.ts  label first, Camunda XML tag underneath
```
