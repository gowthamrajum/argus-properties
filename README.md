# Argus Properties

Reference catalogue of every shape a Camunda 7 / Fluxnova BPMN 2.0 file can contain, and every
property each one carries.

**Each shape is a resource; its properties are a sub-resource.** `GET /api/v1/capabilities` lists
every path.

| Path | What it answers |
| --- | --- |
| `GET /shapes` | What shapes exist? Filter by category, free text, or engine support |
| `GET /shapes/{id}` | What is this shape - tag, notation, constraints, example XML |
| `GET /shapes/{id}/properties` | What can I set on it, inherited and own |
| `GET /shapes/{id}/properties/{name}` | One property: type, default, allowed values, where it lands in the XML |
| `GET /shapes/{id}/behaviour` | What happens when a token reaches it - every outcome, the save point, retries |
| `GET /shapes/{id}/notation` | How it is drawn, and the bounds to emit in BPMN DI |
| `GET /event-shapes` | The ~49 concrete event shapes, derived from the legality matrix |
| `GET /event-shapes/{id}` | One of them, with its composed behaviour |
| `GET /event-shapes/check` | May this position and definition be combined? |
| `GET /categories` | The palette groups, with the shape ids in each |
| `GET /property-groups` | The shared property sets shapes inherit |
| `GET /concepts` | The vocabulary in plain language — what a shape is, what a property is |
| `GET /shapes/{id}/preview` | A real `.bpmn` file containing just this shape — render it with bpmn-js |
| `GET /properties` · `/properties/{name}` | The catalogue indexed by property: which shapes accept this? |

Sibling of `argus-backend` and independent of it. That service **parses** a `.bpmn` file and
judges it; this one **describes** what a `.bpmn` file may contain. Static reference data, no BPMN
model dependency, nothing to upload.

## Coverage

51 shapes across 8 categories.

| Category | Count | Contents |
| --- | --- | --- |
| `CONTAINER` | 9 | process, collaboration, pool, lane, lane set, sub-process, event sub-process, transaction, ad-hoc sub-process |
| `ACTIVITY` | 9 | task, user, service, send, receive, manual, script, business rule, **call activity** |
| `GATEWAY` | 5 | exclusive, parallel, inclusive, event-based, complex |
| `EVENT` | 5 | start, intermediate catch, intermediate throw, boundary, end |
| `EVENT_DEFINITION` | 10 | message, timer, error, escalation, signal, conditional, link, compensate, cancel, terminate |
| `DATA` | 6 | data object reference and definition, data store reference and definition, data input, data output |
| `ARTIFACT` | 2 | text annotation, group |
| `CONNECTION` | 5 | sequence flow, message flow, association, data input/output association |

### Why call activities are activities, not containers

`ACTIVITY` is BPMN's own supertype: the thing that can loop, be compensated, carry data
associations and be interrupted by a boundary event. A call activity does all four. Filing it with
the containers described its plus marker rather than its nature — and put it in a different family
from the tasks it behaves like.

Sub-processes stay containers, because they genuinely hold other shapes; a call activity does not,
it points at a separately deployed process.

Categories carry their own display wording (`label`: "Activities", "Event definitions", "Data"),
so the frontend renders what the service says instead of title-casing the id and inventing
"Datas".

### Why event definitions are shapes

Nobody places a "boundary event" and then picks a type - they place a *timer boundary event*. A
concrete event shape is a position crossed with a definition, and cataloguing the two axes
separately is how the XML works. **`id` is the key, not `tag`.**

The same applies to `bpmn:subProcess`, which is two shapes (`sub-process`, `event-sub-process`)
distinguished by `triggeredByEvent`.

## Event shapes are derived, not declared

Five event tags and ten definitions do not make fifty shapes. Most positions accept only a handful
of definitions, several combinations exist in one direction only - a timer catches but never throws,
a link is a matched pair and appears nowhere else - and `cancelActivity` doubles most of the
boundary family. The answer is **49**, and writing 49 out by hand would be long and would get some
wrong.

So [`EventCompositionRules`](src/main/java/com/argus/properties/catalogue/EventCompositionRules.java)
declares the legality matrix and the shapes are derived from it. There is no way to add a shape the
matrix does not permit, and no way to change the matrix without the shape list moving with it.

| Position | Shapes | Notes |
| --- | ---: | --- |
| start (top level) | 5 | none, message, timer, signal, conditional |
| start (event sub-process) | 12 | adds error, escalation, compensate - and the interrupting axis, because now there is a scope to interrupt |
| intermediate catch | 5 | adds link; no error - an error is caught on a boundary, never mid-flow |
| intermediate throw | 6 | no timer and no conditional: you cannot throw the passage of time |
| boundary | 13 | the largest family |
| end | 8 | adds terminate and cancel |

Every rule is what the Camunda 7 parser accepts, not what the BPMN specification permits in
principle. The two differ, and only the first affects your deployment.

### The boundary family

| Definition | Interrupting | Non-interrupting |
| --- | :---: | :---: |
| message, timer, signal, conditional, escalation | yes | yes |
| error | yes | never - it always cancels its host |
| cancel | yes | never - and only on a transaction |
| compensate | neither: it registers a handler and takes no part in normal flow | |

### Checking a pairing

```bash
curl "http://localhost:8081/api/v1/event-shapes/check?position=end-event&definition=timer-event-definition"
```
```json
{ "legal": false, "reason": "end-event does not accept timer-event-definition. It accepts:
   (none), cancel-event-definition, compensate-event-definition, error-event-definition, …" }
```

A rejection lists what the position *does* accept, because a bare "no" leaves the caller guessing at
what to try next.

### Composed behaviour

The 49 profiles are composed from three tiers rather than written out 49 times:

- **position** - a catch waits, a throw runs straight through, a boundary is armed by its host
  rather than reached by a token
- **definition** - what fires it, and what can go wrong waiting for that
- **the interrupting flag** - what happens to the host when it does fire

So a correction to "how a timer fires" lands on every timer shape at once. And the differences the
split exists for come out properly:

- a **non-interrupting cycle timer** (`R3/PT1H`) fires three times and produces three tokens, while
  the host keeps running - it multiplies the branch rather than repeating in place
- a **compensate boundary event** has no outcome at all during normal flow; it registers a handler
  invoked later
- an **error boundary event** catches a `BpmnError` and *never* a technical exception, which is the
  most common misunderstanding in Camunda 7 and is now stated on the shape

## Stack

| Piece | Choice |
| --- | --- |
| Runtime | Java 21 |
| Framework | Spring Boot 3.5.6 |
| Docs | springdoc-openapi (Swagger UI at `/swagger-ui.html`) |

## Run

One jar serves the API and the documentation site:

```bash
mvn clean package
java -jar target/argus-properties-0.1.0-SNAPSHOT.jar     # :8081
```

`mvn package` builds [`frontend/`](frontend) into `target/classes/static` using the `npm` already on
your PATH — a build that quietly installs its own Node is a build that disagrees with the one you
run by hand. Skip it with `-DskipFrontend` when you only care about the backend.

Working on the UI is nicer with live reload, in which case run the two halves separately:

```bash
mvn spring-boot:run                 # :8081, API only
cd frontend && npm start            # :4200, proxies /api to :8081
```

| | |
| --- | --- |
| Documentation site | <http://localhost:8081/> |
| API docs | <http://localhost:8081/swagger-ui.html> |
| Health | <http://localhost:8081/actuator/health> |

Port `:8081` leaves `:8080` free for `argus-backend`.

Deep links like `/shapes/user-task` are served by
[`SpaConfig`](src/main/java/com/argus/properties/config/SpaConfig.java), which hands back the Angular
shell for client-side routes. The fallback deliberately stops at `/api` and `/actuator`: an unknown
shape id must keep returning the service's JSON error, not an HTML page.

## API

### `GET /api/v1/shapes`

```bash
curl http://localhost:8081/api/v1/shapes
curl "http://localhost:8081/api/v1/shapes?category=GATEWAY"
curl "http://localhost:8081/api/v1/shapes?q=boundary"
curl "http://localhost:8081/api/v1/shapes?executable=false"     # drawn by modellers, ignored by the engine
```

`q` searches id, name, tag and summary, because people look for `boundary`, `camunda:assignee` and
`bpmn:userTask` interchangeably. An unknown `category` is a 404, not an empty list - an empty list
would read as "there are no gateways", which is a different answer.

### `GET /api/v1/shapes/{id}/properties`

```bash
curl http://localhost:8081/api/v1/shapes/user-task/properties
curl "http://localhost:8081/api/v1/shapes/user-task/properties?own=true"
curl "http://localhost:8081/api/v1/shapes/user-task/properties?namespace=camunda"
```

Returns the **effective** set: inherited first, each stamped with the group it came from, then the
shape's own. `?own=true` leaves only what makes this shape different from every other flow node;
`?namespace=camunda` leaves only the vendor extensions.

```json
{
  "shapeId": "user-task",
  "tag": "bpmn:userTask",
  "propertyCount": 35,
  "countsByNamespace": { "bpmn": 15, "camunda": 20 },
  "countsByKind": { "ATTRIBUTE": 22, "CHILD_ELEMENT": 7, "EXTENSION_ELEMENT": 6 },
  "properties": [
    { "name": "id", "kind": "ATTRIBUTE", "required": true, "inheritedFrom": "base-element", "...": "" },
    { "name": "camunda:assignee", "kind": "ATTRIBUTE", "namespace": "camunda", "type": "expression", "...": "" }
  ]
}
```

### `GET /api/v1/shapes/{id}/properties/{name}`

The name includes its prefix. A colon is a legal path segment character:

```bash
curl http://localhost:8081/api/v1/shapes/user-task/properties/camunda:formRefBinding
```
```json
{ "name": "camunda:formRefBinding", "kind": "ATTRIBUTE", "namespace": "camunda", "type": "enum",
  "defaultValue": "latest", "allowedValues": ["latest", "deployment", "version"] }
```

### `GET /api/v1/shapes/{id}/notation`

What a DI generator needs - the geometry a modeller creates the shape at:

```json
{ "diElement": "bpmndi:BPMNShape", "defaultWidth": 36, "defaultHeight": 36, "resizable": false,
  "render": "double-line circle on an activity border; solid = interrupting, dashed = non-interrupting" }
```

## Indexed both ways

Shape-first answers *what can I set here?*. It does not answer the question people actually arrive
with — *which shapes support task listeners?* — because a property contributed by a group is
invisible in a shape's own declarations. `GET /properties` is the same data along the other axis:

```bash
curl "http://localhost:8081/api/v1/properties?kind=EXTENSION_ELEMENT"
curl "http://localhost:8081/api/v1/properties/camunda:taskListener"
```

Descriptions are carried **per shape**, not once, because the same property does not always mean
quite the same thing: an execution listener on a sequence flow fires on `take`, on a flow node it
fires on `start` or `end`. One sentence for both would lose the only part worth reading.

## Previews are real BPMN, not pictures of BPMN

`GET /shapes/{id}/preview` returns a genuine `.bpmn` document containing one shape. Hand it to
bpmn-js and you get exactly what bpmn.io draws; save it and Camunda Modeler opens it.

The alternative — approximating each shape in the frontend — drifts from the real renderer the
moment either side changes, and drifts *silently*, because a diamond that is subtly wrong still
looks like a diamond. Generating BPMN means nothing in the UI has to know what a gateway looks like.

Geometry comes from the shape's own `notation`, so the picture and the documented default bounds
cannot disagree. Shapes that need context to be drawable get it: a boundary event gets a task to
attach to, a message flow gets two pools, an event definition gets a host event to sit inside.
The five that are never drawn — `process`, `collaboration`, `lane-set`, `data-object`, `data-store`
— answer 404 with the reason.

### Validated against a real parser

`BpmnPreviewTest` parses every generated preview with `fluxnova-bpmn-model` (**test scope only** —
the service still never parses BPMN in production). Generating files it could not itself validate
would be the sort of claim that quietly stops being true. It earned its keep immediately, catching
three schema violations a string comparison would have missed:

- `bpmn:conditionalEventDefinition` requires a `condition` child
- `bpmn:textAnnotation` forbids a `name` attribute — artifacts are not flow elements
- `bpmn:dataInputAssociation` requires a `targetRef`, which is why bpmn-js emits a
  `__targetRef_placeholder` property

## Every property has two names

The XML name is exact and unreadable: `camunda:isStartableInTasklist` is not a phrase anyone says
out loud, and a table of a hundred of them is a wall of camelCase. But it is also what you type
into the file, so it can only be accompanied, never replaced. Each property therefore carries a
`label` to read, the `name` to type, and an `example` to copy:

```json
{
  "label": "Retry time cycle",
  "name": "camunda:failedJobRetryTimeCycle",
  "example": "R3/PT10M",
  "kind": "EXTENSION_ELEMENT",
  "namespace": "camunda"
}
```

Labels derive mechanically from the XML name, so a new property gets a sensible one for free.
[`PropertyLabels`](src/main/java/com/argus/properties/catalogue/model/PropertyLabels.java) overrides
that in two cases: where the derived form reads badly (`id` → "Id", `isExecutable` → "Is
executable"), and — more importantly — **where Camunda's own Modeler already has a word for it**.
`camunda:asyncBefore` is "Asynchronous before" because that is what the Modeler calls it. A
catalogue that invents its own vocabulary for a tool people already use is worse than no catalogue.

Examples live in the same shape of table
([`PropertyExamples`](src/main/java/com/argus/properties/catalogue/model/PropertyExamples.java)) —
one map rather than a literal repeated across eight catalogue files. Enums have no example because
their `allowedValues` already are one. `validate()` fails startup on a label or example whose
property no longer exists, so a rename cannot leave dead vocabulary behind.

## Behaviour

A property list says what you may *configure*. A behaviour profile says what the engine will then
*do* — which is the part that decides whether an instance completes, stalls, or ends up in front of
an operator.

```bash
curl http://localhost:8081/api/v1/shapes/parallel-gateway/behaviour
```

```jsonc
{
  "executionKind": "ROUTING",
  "savePoint": "ON_ASYNC",
  "outcomes": [
    { "id": "COMPLETED", "trigger": "Splitting: the token arrives",
      "effect": "One token on every outgoing flow, unconditionally. Conditions are ignored…" },
    { "id": "STUCK",
      "trigger": "Joining: an incoming branch can never deliver a token, because it sits behind
                  an exclusive split that chose a different path",
      "effect": "The join waits forever. No error, no incident, nothing in the logs…",
      "recovery": "None at run time. The model has to change…" }
  ],
  "retries": { "retriesTechnicalFailures": false, "defaultRetries": 0, "note": "…" },
  "notes": ["A conditional flow leaving a parallel gateway is a silent no-op…"]
}
```

### The three things a profile states

**`executionKind`** — `SYNCHRONOUS`, `WAIT_STATE`, `ROUTING`, `PASS_THROUGH`, or
`IMPLEMENTATION_DEPENDENT`. A service task is the last one and genuinely so: a Java delegate runs
inline, `camunda:type=external` turns the same shape into a wait state.

**`savePoint`** — `ALWAYS`, `ON_ASYNC`, `NEVER` or `IMPLEMENTATION_DEPENDENT`. This is what "async
point" actually means: the engine commits at wait states and at `camunda:asyncBefore`/`asyncAfter`,
and everything between two save points is one transaction. It is what decides how far a failure
unwinds — a failure in the sixth activity of a synchronous chain undoes the first five, including
the REST call the third one made.

**`outcomes`** — every way the token can leave, or fail to:

| Outcome | Means |
| --- | --- |
| `COMPLETED` | The token leaves. The only outcome that is just progress |
| `WAITING` | Parked, resumable by an external trigger |
| `BPMN_ERROR` | Modelled failure — catchable by a boundary event, never retried |
| `INCIDENT` | Technical failure, retries exhausted, operator needed. At least it is visible |
| `ROLLBACK` | Technical failure with no save point in front of it; the transaction unwinds |
| `STUCK` | Parked with no trigger that can ever arrive. The dangerous one |
| `UNSUPPORTED` | The engine has no implementation; the model will not deploy |

`STUCK` earns its place. An incident shows up in Cockpit; a user task nobody can see, an external
task nobody serves, and a parallel join waiting on a branch that never ran all look exactly like a
healthy instance that has not finished yet. Every `STUCK` outcome must state a `recovery` — the
catalogue fails to boot otherwise, because describing a silent stall without saying how to get out
of it is the least useful thing it could do.

`retries` is stated per shape because the answer is not uniform: three attempts back-to-back by
default, respaced by `camunda:failedJobRetryTimeCycle`, never for a `BpmnError`, and irrelevant
where no job exists.

### What it deliberately does not model

Engine behaviour only. That is deterministic and documented. What `ValidateOrderDelegate` does with
a null customer id is application code the catalogue cannot see, so a profile says "a technical
exception here retries three times and then raises an incident" and stops.

### Coverage

**13 of 51 shapes** — all 8 tasks and all 5 gateways. Everything else returns **404**, not an empty
body: "not catalogued yet" and "this shape does nothing" are different answers, and an empty body
reads as the second. The 404 lists what is covered.

Tasks and gateways first on purpose. Events are the larger group but a boundary event's behaviour
depends entirely on which event definition is clipped into it — a timer boundary and an error
boundary have almost nothing in common — so composite shapes have to be resolvable before event
behaviour can be written against the right unit.

## What a "property" means here

Wider than "XML attribute", because a modeller's property panel does not distinguish between them
and neither should a caller asking "what can I configure?". `kind` preserves where the value
actually lands, which is what a serialiser needs:

| `kind` | Lands in | Example |
| --- | --- | --- |
| `ATTRIBUTE` | on the element | `camunda:assignee` |
| `CHILD_ELEMENT` | a child of it | `bpmn:conditionExpression` |
| `EXTENSION_ELEMENT` | inside `<bpmn:extensionElements>` | `camunda:taskListener` |
| `DI_ATTRIBUTE` | on the `bpmndi:BPMNShape`, not the semantic element | `isExpanded`, `isMarkerVisible` |

That last row is worth its own note: some things that look like model properties are diagram-only.
An exclusive gateway's `X` is `isMarkerVisible` on the DI, so two files with identical execution
semantics can render differently. A collapsed sub-process is `isExpanded=false` - it still contains
and executes everything inside it.

## Property groups are membership-precise

Groups are not cosmetic families - each one's membership is the set of shapes the engine actually
accepts the property on, checked against `BpmnParse` rather than inferred from the BPMN spec:

| Group | On | Because |
| --- | --- | --- |
| `camunda-async` | 21 flow nodes | scheduling: `asyncBefore/After`, `exclusive`, `jobPriority`, `failedJobRetryTimeCycle` |
| `camunda-extensions` | 21 flow nodes | `executionListener`, `properties` - accepted anywhere a token stops |
| `camunda-io-mapping` | **16 shapes** | `camunda:inputOutput` only, see below |

`camunda:inputOutput` gets its own group because
[`BpmnParse.checkActivityInputOutputSupported`](https://github.com/camunda/camunda-bpm-platform/blob/master/engine/src/main/java/org/camunda/bpm/engine/impl/bpmn/parser/BpmnParse.java)
accepts a tag whose name contains `task` or `Event`, plus `subProcess`, `transaction` and
`callActivity` - and rejects everything else at **deploy time**:

```
camunda:inputOutput mapping unsupported for element type 'exclusiveGateway'.
```

So gateways and sequence flows are out, and so is an **event sub-process** - excluded by name
despite being a `subProcess`. Two further restrictions apply to output parameters only, and are
recorded as constraints on the shapes they affect: `camunda:outputParameter` is rejected on an
**end event**, and on any **multi-instance** activity, where each instance would overwrite the last.

```bash
curl "http://localhost:8081/api/v1/shapes/exclusive-gateway/properties?namespace=camunda"
```

## `executable`

Whether the Camunda 7 / Fluxnova **engine acts on the shape**. 13 of the 51 are drawn by every
modeller and ignored by the engine - complex gateway, ad-hoc sub-process, data objects and stores,
groups, message flows, data associations. Flagging it in the catalogue means you find out before
building a model around one, rather than after deploying it:

```bash
curl "http://localhost:8081/api/v1/shapes?executable=false"
```

## Why the catalogue is Java, not a JSON resource

Same reason `argus-backend` declares its rules in `RuleCatalogue`: the catalogue *is* the product,
so it should be reviewable as code, diffable between releases, and wrong in ways a compiler and a
test can catch. Declarations live one class per family under
[`catalogue/`](src/main/java/com/argus/properties/catalogue) - nobody reviews a single file listing
51 shapes carefully.

`ShapeCatalogue.validate()` runs at startup (`@PostConstruct`) and **fails the boot** on an unknown
inherited group, a missing summary or notation, a duplicate property, or an undescribed one. A shape
inheriting a group that does not exist would otherwise serve a silently shorter property list - the
caller sees no error and has no way to know.

## Test

```bash
mvn test
```

44 tests. Beyond the routing, they pin the invariants that keep reference data honest: every default
is one of its own `allowedValues`; every drawn shape has bounds and every edge has none; every
async-capable flow node inherits `camunda:asyncBefore`; ids are unique while tags need not be; and
`camunda:inputOutput` resolves on exactly the 16 shapes the parser accepts it on.

`validate()` also rejects a property that resolves twice - from two groups, or from a group and the
shape itself. That check found a real one: `business-rule-task` served `camunda:resultVariable`
twice with contradicting descriptions.

Derived event shapes are validated too: the factory refuses two rules that would produce the same
id, and every composed profile faces the same completeness checks as a hand-written one.

Behaviour adds its own: a profile that is present must be complete. Unknown outcome id, unknown
execution kind, no retry profile, a `STUCK` with no recovery, or no `COMPLETED` outcome at all — any
of them fails the boot. That last one matters because a profile listing only failures is describing
half a shape. Coverage itself is pinned by a test rather than inferred, so adding a shape without a
profile is a visible decision instead of a quiet gap. Writing these caught a real omission:
`send-task` described its stuck state without saying how to get out of it.
