# Architecture

A modular monolith hosts one Spring Boot application and one PostgreSQL database; the React client uses same-origin `/api` and `/ws` via Nginx. PostgreSQL is authoritative. Docker Compose waits for database health, a successful dedicated Flyway migration and backend readiness, in that order. Actuator exposes health probes; JPA validates instead of creating schema. Flyway is not an application dependency and runs only in the migration container.

Planned backend features: `auth` (identity and access), `event` (event state), `participant` (registration and identity), `draw` (selection), `wheel` (prizes), `quiz` (Mohammed's ownership), `notification` (notifications); `common` holds shared errors, `config` infrastructure configuration. Auth, participant, draw, wheel, and real-time infrastructure are implemented; remaining business features are planned. Planned frontend boundaries are auth, participant, admin, draw, wheel, quiz; shared UI, layouts, hooks, services, styles, assets and types support them. Empty feature folders are not tracked until they contain work.

Authentication uses Spring Security, BCrypt and PostgreSQL-backed Spring Session JDBC with Flyway-owned tables. An administrator bootstrap is serialized with a PostgreSQL transaction advisory lock. Security contexts use a serializable principal without a password hash after authentication. Sessions can be shared across instances without sticky routing. JWT is not used. CSRF is required on state-changing requests, including login and logout. Default cookie flags are HttpOnly, SameSite=Lax, and Secure; the dev profile permits HTTP. All unapproved routes are denied. The `/ws` endpoint uses session-authenticated STOMP with role-restricted subscriptions.

The login limiter is bounded and instance-local, keyed on the direct peer address, not forwarded headers. It counts attempts within a fixed window and resets on success; coordinated limiting and trusted proxy configuration must be designed before multi-instance production. Behind a proxy, all clients may share a bucket. Deploy with HTTPS and the prod profile.


Participant registration reuses the Spring Security principal, fixation protection, CSRF strategy and JDBC session store. `participants` stores a UUID and an independently allocated PostgreSQL sequence number. The sequence is safe across concurrent requests and restarts; rollbacks can leave gaps. A SHA-256 digest of the original established session ID is mapped to the participant under a PostgreSQL transaction advisory lock, so concurrent retries cannot create duplicate records across instances. No raw session IDs are stored in this mapping. Rows persist after session expiry and currently require an eventual retention policy. Separate sessions/devices cannot be recognized as the same person; registration is not proof of identity. The application and migrations must be tested on PostgreSQL with real concurrent Spring Session requests before production use.

Real-time notifications are sent through a single-instance Spring Simple Message Broker. `RealtimeEvents` schedules typed notifications via Spring application events and an AFTER_COMMIT listener; rolled-back changes do not broadcast. PostgreSQL remains authoritative, delivery is best-effort, and clients resynchronize with REST. HTTP sessions are shared by JDBC but broker messages are not shared between instances; deploy one backend task. Per-message and periodic session validation prevent stale sessions from continuing to receive data. See [real-time contract](REALTIME_CONTRACT.md).

## Draw engine (F04)

PostgreSQL stores cycles and draw history. A partial unique index permits at most one open cycle, another permits at most one ACTIVE draw globally, and `(cycle_id, participant_id)` is unique even for cancelled draws. Check constraints enforce valid status/timestamp combinations; foreign keys preserve participant and administrator accountability. A singleton `draw_control` row is locked `FOR UPDATE` for start, cancel, completion and reset, serializing state changes across backend instances. Each operation occurs in one transaction. The F04 algorithm loads eligible UUIDs and uses `SecureRandom.nextInt(bound)` uniformly; the eligible set is the snapshot seen by its query, so a concurrent registration becomes eligible after its commit is visible. Presence never affects eligibility. Reset closes the current cycle and opens the next, with an expected-cycle check to reject duplicate requests. Read snapshots for status and history use repeatable-read isolation.

F05 must call `DrawService.lockActiveForPrizeAllocation()` at the beginning of its own transaction, persist an award protected by a **unique database constraint on draw_id**, then call `completeAfterPrizeAllocation(drawId)` in that same transaction. Both APIs require an existing transaction and acquire the same control lock. F04 has no award table and cannot itself verify that an allocation actually occurred or enforce the future award uniqueness constraint. There is no completion HTTP endpoint. All components must acquire the draw control lock before prize-specific locks to avoid lock-order deadlocks.

Draw state events reuse F03's AFTER_COMMIT publisher; a failed or missed notification does not roll back committed draw state, and REST remains the recovery source. F03's in-memory broker is single-instance even though the draw database locking itself supports multiple instances. F04 has not been integration-validated until its tests execute against PostgreSQL.

## Prize wheel (F05)

`wheel` stores inventory, one persistent progression/config row, award snapshots and a stock adjustment audit. Spin, inventory and configuration operations acquire `draw_control`, then `wheel_configuration`, then prize rows in UUID order. Spin inserts a unique award per draw, decrements stock, updates progression and calls F04 completion in one transaction. The database enforces nonnegative stock and valid weights, categories and probability. F03 publishes typed wheel/admin/private events after commit; REST remains authoritative. Draw reset is independent of all wheel state. See [PRIZE_WHEEL.md](PRIZE_WHEEL.md) for numeric bounds, precision and unvalidated integration limitations.

## Quiz integration boundary

Mohammed's Quiz will reuse the modular monolith, participants, session principal, PostgreSQL/Flyway and `/ws` infrastructure. `quiz/` and `features/quiz/` are reserved with documentation; no quiz behavior, UI, routes or tables exist. Shared frontend `services/api.ts` handles session cookies and CSRF; `services/realtime.ts` manages one STOMP connection with bounded reconnect and subscription restoration. Quiz state still comes from future REST endpoints, not messages. V7–V19 are reserved for Quiz, subject to coordinated Flyway deployment order. See [QUIZ_INTEGRATION.md](QUIZ_INTEGRATION.md).

### P01 participant registration frontend

`frontend/src/features/participant` owns the registration view, name validation, session context and thin registration API functions. The existing shared API client remains responsible for cookies and CSRF. Poster-derived assets are located in `frontend/src/assets/participant`; the artwork is decorative and the official partner strip has descriptive alternative text. The `/participant` route hosts P02, with a reusable header, pass and four-item bottom navigation. Draw state comes from REST and is refreshed on relevant private and shared draw events, reconnection, and 30-second visible-page polling. Quiz and dedicated draw and wheel screens remain future work. Live registration and responsive visual behavior require verification against a running backend and browser.

### A02 administrator frontend

The ADMIN-protected `/admin` dashboard uses `AdminLayout` for reusable navigation and the existing API, CSRF and shared STOMP clients. Participant counts, current-cycle draw counts and eligible stock are retrieved independently from the backend. Relevant `/topic/admin` and `/topic/draw` events trigger REST resynchronization; the draw start mutation is never retried automatically. A disconnected broker pauses the draw control until reconnection. A03–A06 interfaces remain unimplemented.

### A03 Lucky Draw management

The role-protected `/admin/draw` page reuses `AdminLayout` and the official A02 header. It calls the existing draw status, start, cancel, reset and paginated history endpoints, plus participant count; confirmations precede cancel/reset, and the reset includes the last authoritative expected cycle number. STOMP events and reconnects trigger REST refresh; failed or uncertain mutations are not automatically repeated. A04 remains unimplemented.

### A05 administrator prize management

`/admin/prizes` uses the ADMIN route guard and shared administrator layout, API and STOMP services. Inventory and probability configuration come from separate REST endpoints; mutations refresh authoritative state, are never retried automatically, and are disabled when a refresh fails. Wheel probability progression and award history remain backend-owned.

### A06 participant administration

`/admin/participants` is an ADMIN-protected read-only view. It retrieves all participant pages sequentially (backend limit 100) for whole-list local search, deduplicates by persistent ID and paginates the filtered results. The total comes from the dedicated count endpoint. Registration events and reconnection cause REST refresh using the shared STOMP service. These requests do not form one database snapshot; live concurrent-registration behavior remains to be validated.
