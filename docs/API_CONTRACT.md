# Shared API contract

Authentication, participant, draw and wheel endpoints below are implemented; other business endpoints remain planned. WebSocket infrastructure is described in [REALTIME_CONTRACT.md](REALTIME_CONTRACT.md). The platform and Mohammed's quiz module must share authentication, participant identity and WebSocket infrastructure; the quiz module is reserved for Mohammed.

| Concept | Planned convention |
| --- | --- |
| REST | `/api` |
| Administration | `/api/admin` |
| WebSocket endpoint | `/ws` |
| Shared event topic | `/topic/event` |
| Participant identifier | UUID |
| Participant public number | positive integer |
| Event identifier | BIGINT |
| Roles | ADMIN, PARTICIPANT |
| Authentication | HTTP session cookie, Spring Session JDBC |
| Timestamps | UTC, ISO 8601 |
| Payload | JSON |
| SQL names | snake_case |
| Java and TypeScript names | camelCase |

## Authentication API

| Method and path | Access | Result |
| --- | --- | --- |
| `GET /api/auth/csrf` | Public | 200 JSON: `token`, `headerName`, `parameterName` |
| `POST /api/auth/admin/login` | Public, CSRF required | JSON `username`, `password`; 200 JSON `id`, `username`, `role`; 401 invalid credentials; 429 throttled |
| `GET /api/auth/me` | Authenticated | 200 identity JSON; 401 without a session |
| `POST /api/auth/logout` | Authenticated, CSRF required | 204; session invalidated |
| `/api/admin/**` | ADMIN | 401 unauthenticated; 403 wrong role |

Get a CSRF token before login, send it in the returned header, and obtain a new token after login and logout. Missing or invalid tokens return 403. Validation failures return 400 problem details. Security failures return JSON with `status` and `error`. Other routes are denied by default. The quiz module must reuse these shared session and identity contracts. WebSocket subscriptions require an authenticated session and STOMP CONNECT CSRF token.


## Participant registration API

| Method and path | Access | Result |
| --- | --- | --- |
| `POST /api/participants/register` | Public, CSRF required | JSON `firstName`, `lastName`; 201 for a new participant; 200 for the existing participant in this session; 400 invalid data; 403 for admin or missing CSRF |
| `GET /api/participants/me` | PARTICIPANT | 200 participant identity; 401 unauthenticated; 403 wrong role |
| `GET /api/admin/participants?page=0&size=20` | ADMIN | 200 paginated participants ordered by number; page >= 0 and size 1–100 |
| `GET /api/admin/participants/count` | ADMIN | 200 JSON `{"count": 42}` |

Registration and participant identity responses contain `id` (UUID), `participantNumber` (positive integer), `firstName`, `lastName` and `role` (`PARTICIPANT`). `/api/auth/me` returns the same participant shape for a participant; the admin response remains unchanged. Names are trimmed and limited to 100 characters, and duplicates are allowed. The authenticated principal determines `/me`; client-supplied IDs are never trusted.

Clients must obtain `/api/auth/csrf` first, retain the cookie and send its token header on registration. Authentication rotates the session and clears the CSRF token: obtain a fresh one for subsequent state-changing requests. Repeated requests from an established registration session resolve to the same participant, including concurrent requests. Separate sessions, browsers and devices are not a reliable indicator of a unique person; losing a session cannot restore the identity. A PostgreSQL sequence guarantees unique numbers under concurrency but gaps can occur on rollback.

Future draw, wheel, notification and quiz modules may use `ParticipantService.findById`, `list`, and `count`; UUID is the internal identity and participantNumber is the public identifier. Do not infer authorization from the public number.

## Draw API (F04)

All `/api/admin/draw/**` endpoints require `ADMIN`; all POSTs require CSRF. Business conflicts return RFC 9457 problem details with a `code` such as `DRAW_ACTIVE`, `NO_PARTICIPANTS`, `NO_ELIGIBLE_PARTICIPANTS`, `STALE_CYCLE`, `INVALID_DRAW_TRANSITION` or `DRAW_CONFLICT`.

| Method and path | Result |
| --- | --- |
| `POST /api/admin/draw/start` | 201 active draw and admin-visible participant; no selection parameters accepted; 409 on an active draw or no eligible participants |
| `GET /api/admin/draw/status` | Current cycle, registered/selected/eligible counts, active draw, `canStartDraw`, `canReset` |
| `POST /api/admin/draw/reset` | JSON `{ "confirmed": true, "expectedCycleNumber": 1 }`; returns previous/current cycle and counts; 400 invalid confirmation, 409 active or stale cycle |
| `POST /api/admin/draw/{drawId}/cancel` | Cancels ACTIVE draw; 404 unknown draw, 409 invalid transition |
| `GET /api/admin/draw/history` | `{content, page, size, totalElements}`; optional `cycleNumber`, `status`, `page` and `size` (1–100); descending creation order |
| `GET /api/draw/current` | ADMIN or PARTICIPANT; cycle, active flag, active draw's public number and status only |
| `GET /api/participants/me/draw` | PARTICIPANT only; own current-cycle selection, active flag, draw ID and status |

A cancelled participant remains selected in that cycle. Reset closes the current cycle and preserves all prior draws and participant records. The initial cycle is 1; a reset may be early if no draw is active. Duplicate names are allowed and never determine eligibility. Public numbers are not authentication credentials.

## Prize wheel API (F05)

Every `/api/admin/prizes/**` and `/api/admin/wheel/**` route requires ADMIN; mutations require CSRF. Validation errors are 400; missing resources 404; stock, state and concurrency conflicts 409. Responses use problem details with codes on domain errors.

| Method and path | Purpose |
| --- | --- |
| `POST /api/admin/prizes` | Create `{name,description,category,baseWeight,initialStock,enabled}`; 201 |
| `GET /api/admin/prizes`, `GET /api/admin/prizes/{id}` | Inventory |
| `PATCH /api/admin/prizes/{id}` | Change name, description, category, baseWeight, enabled; stock fields are rejected |
| `POST /api/admin/prizes/{id}/stock` | `{quantityDelta,reason}`; audited signed stock adjustment |
| `GET /api/admin/wheel/config`, `PATCH /api/admin/wheel/config` | Read or set `{geometricGrowthFactor,maximumPremiumMultiplier}` without editing progression |
| `GET /api/admin/wheel/status` | Active draw, progression, multiplier, eligible stock and probabilities; a point-in-time snapshot |
| `POST /api/admin/wheel/spin` | `{drawId}` only; 201 new award, 200 idempotent retry, 409 if not active or no eligible prizes |
| `GET /api/admin/wheel/history` | Paginated snapshots; filters `participant`, `prize`, `category`, `draw`, `page`, `size` (1–100) |
| `GET /api/wheel/status` | Authenticated ADMIN/PARTICIPANT; public state and most recent persisted result |
| `GET /api/participants/me/prizes` | PARTICIPANT only; own paginated public award history |

Award response fields are `awardId`, `drawId`, `participantId` (admin only), `participantNumber`, `prizeId`, `prizeName`, `prizeCategory`, `awardedBy`, `awardedAt` and admin snapshot details. Public responses omit the participant UUID and inventory/weight details. Segments are snapshotted in deterministic UUID order; display sectors are equal and must disclose the weighted selection. See [prize wheel](PRIZE_WHEEL.md).

## Proposed Quiz namespace — not implemented

Administrator operations are proposed under `/api/admin/quiz/**`; shared participant-facing reads under `/api/quiz/**`; a participant's answer and score operations under `/api/participants/me/quiz/**`. Exact routes, schemas and response codes remain for Mohammed to refine. The admin prefix is protected by existing security rules. The other proposed namespaces are currently **denied by default** and need explicitly reviewed security matchers before implementation. Resolve identity through `SessionPrincipal`, validate CSRF on mutations, return existing problem/security error formats, and never expose correct answers while a question is open. See [QUIZ_INTEGRATION.md](QUIZ_INTEGRATION.md).

### P01 frontend integration

The registration page at `/` checks `GET /api/auth/me`. A participant is redirected to the `/participant` home; an administrator remains signed in and cannot submit registration. An anonymous visitor submits trimmed first and last names to `POST /api/participants/register`; both 201 (created) and 200 (existing) responses provide the authoritative participant identity and number. The shared frontend API client fetches `/api/auth/csrf` and supplies its returned header for mutations with session cookies. It does not automatically retry registration. The participant home uses `GET /api/participants/me/draw` for authoritative eligibility; Quiz and dedicated Draw screens are not yet available.

### A01 administrator frontend

`/admin/login` reuses `/api/auth/me`, `/api/auth/admin/login` and `/api/auth/logout` with the shared CSRF-aware client. It verifies the ADMIN session after login and provides an explicit participant-session logout before login. `/admin` is a role-checked frontend holding page; backend API authorization remains authoritative. The client-side session check cannot prevent an identity change in another tab between its check and the login request; the login endpoint currently accepts an existing participant session. A server-side guard and concurrency test are needed for a strict no-replacement guarantee.

### A06 administrator participant view

The read-only `/admin/participants` frontend uses the ADMIN endpoints `GET /api/admin/participants?page=...&size=...` and `GET /api/admin/participants/count`. The backend sorts by public participant number and limits page size to 100. The current participant response does not include registration time. The UI loads all pages sequentially for search and uses the dedicated count response for the total; it does not create or modify participants.
