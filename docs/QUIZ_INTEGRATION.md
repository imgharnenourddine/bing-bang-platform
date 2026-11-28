# Quiz integration and developer onboarding

## 1. Project and ownership

THE BIG BANG is a modular monolith for the ENSET event. The Tech Lead owns the Quiz backend, frontend feature, Quiz migrations, tests and Quiz-specific documentation; preserve any work previously produced by Mohammed. The Tech Lead owns authentication, participant identity, real-time infrastructure, draw, wheel, shared frontend services, general participant/admin shells, deployment and cross-module integration. Coordinate changes to `SecurityConfig.java`, WebSocket configuration, shared event types, `pom.xml`, `frontend/package.json`, `frontend/package-lock.json`, `frontend/src/main.tsx` routing, shared tokens, Compose, CI and `AGENTS.md`.

Use the existing Spring Boot application, React app and PostgreSQL database. Do not add a second backend, frontend, identity table, authentication system, JWT or broker. The package boundary is `backend/src/main/java/ma/enset/bigbang/quiz/`; organize controllers, services, repositories, entities, DTOs, exceptions and events as needed. The frontend boundary is `frontend/src/features/quiz/`. F06.1 adds Quiz entities and V7 SQL; ADMIN preparation controllers and services are implemented; Quiz screens and lifecycle services remain pending.

## 2. Prerequisites and Git

Java 21, Maven Wrapper (included in `backend/`), Node 22.14.0 and npm, Docker Engine with Compose, and Git are required. PostgreSQL 16 and Flyway run through Compose. The team also uses Spring Security, Spring Session JDBC, Spring Data JPA, WebSocket/STOMP, React, TypeScript, Vite and Tailwind.

The following commands are historical onboarding examples; the Tech Lead now owns Quiz development:

```bash
git clone https://github.com/imgharnenourddine/bing-bang-platform.git
cd bing-bang-platform
git switch -c feature/quiz origin/main
```

The Tech Lead works independently on `fix/backend-integration`, then `feature/participant-ui` and `feature/admin-dashboard`. Use a feature branch, focused commits and a pull request; no direct commits to main or force-pushes to shared branches without agreement. Coordinate contracts and migration versions before integration. Do not commit `.env`, passwords or private data. These are manual Git instructions; no remote action is performed by this preparation.

## 3. Environment and first administrator

From the repository root, copy `.env.example` to `.env` and edit it locally. Set `POSTGRES_DB`, `POSTGRES_USER`, a unique `POSTGRES_PASSWORD`, and on first backend startup set **both** `ADMIN_USERNAME` and a strong `ADMIN_PASSWORD` of 12–72 UTF-8 bytes. Remove the bootstrap credentials from `.env` after the account is provisioned; restarts do not overwrite its password. `SESSION_COOKIE_SECURE=false` is only for local HTTP; production uses HTTPS and secure cookies. The example includes exact WebSocket origins `http://localhost:8088,http://localhost:5173`. Update an existing `.env` if it predates this change; an origin with a different host or port is different. `APP_PORT` defaults to 8080, `WEB_PORT` to 8088 and optional `DB_FORWARD_PORT` to 5433.

```bash
cp .env.example .env
# Edit .env locally and choose your own credentials.
```

The backend fails startup on an empty database unless both bootstrap variables are configured. Flyway runs in its own container before Spring Boot; Hibernate uses validation, not schema creation. Existing migrations V1–V6 must never be edited after application.

## 4. Option A — full Compose

```bash
docker compose up --build -d
docker compose ps
docker compose logs backend flyway
# Stop, preserving the database volume:
docker compose down
```

Frontend: `http://localhost:8088/`; API through the proxy: `http://localhost:8088/api`; backend: `http://localhost:8080`; health: `http://localhost:8080/actuator/health`. PostgreSQL is not exposed on the host in default Compose. F07 changes the frontend healthcheck to the explicit IPv4 `/healthz` endpoint and supplies disposable administrator credentials to CI. The previous unhealthy cause is not confirmed, and the correction still needs a real Docker run. Check actual logs/status. CI also runs backend verify and frontend lint, typecheck and build.

## 5. Option B — PostgreSQL and Flyway in Docker, backend and Vite locally

The optional override publishes PostgreSQL to **127.0.0.1 only**, by default port 5433. It is not used by default Compose.

```bash
docker compose -f compose.yaml -f compose.local.yaml up -d postgres
docker compose -f compose.yaml -f compose.local.yaml run --rm flyway
```

In a shell from the project root, export the same `.env` values for Spring. The following assumes `.env` uses shell-compatible assignments; quote special characters when editing. Avoid printing these values or putting real credentials in commands or source control.

```bash
set -a
. ./.env
set +a
export DB_URL="jdbc:postgresql://localhost:${DB_FORWARD_PORT:-5433}/${POSTGRES_DB}"
export DB_USERNAME="$POSTGRES_USER"
export DB_PASSWORD="$POSTGRES_PASSWORD"
export SPRING_PROFILES_ACTIVE=dev
cd backend
./mvnw spring-boot:run
```

On first startup the exported `ADMIN_USERNAME` and `ADMIN_PASSWORD` are needed; remove them from `.env` and the shell after provisioning. Backend `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` differ from Compose's internally mapped PostgreSQL variables. In a second terminal:

```bash
cd frontend
npm ci
npm run dev -- --port 5173 --strictPort
```

Open `http://localhost:5173/`. `frontend/vite.config.ts` proxies `/api` to `http://localhost:8080` and `/ws` to `ws://localhost:8080` with upgrades enabled; this keeps browser API requests same-origin. If `APP_PORT` or `DB_FORWARD_PORT` changes, update the local URL/proxy accordingly; Vite currently targets backend port **8080**. Stop the local servers with Ctrl+C and stop the containers from the root with `docker compose -f compose.yaml -f compose.local.yaml down` (volume retained).

## 6. Shared backend identity and participant data

Authenticated HTTP controllers can use Spring Security's actual `SessionPrincipal`. New quiz routes need explicit security matchers in `SecurityConfig`: `/api/admin/**` is already ADMIN-only; `GET /api/quiz/status` is PARTICIPANT-only; other `/api/quiz/**` and `/api/participants/me/quiz/**` routes match `denyAll`. Coordinate the minimal matcher changes with the Tech Lead. CSRF applies to mutations. Never trust an identity from a body, URL or STOMP header as proof of identity.

```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import ma.enset.bigbang.auth.SessionPrincipal;
import ma.enset.bigbang.participant.ParticipantService;

// Inside an explicitly authorized participant controller:
public Object example(@AuthenticationPrincipal SessionPrincipal principal) {
    var participant = participantService.findById(principal.id());
    return participant; // ParticipantResponse: id, participantNumber, firstName, lastName, role
}
```

Inject `ParticipantService`; `findById(UUID)` returns the shared `ParticipantResponse`, `list(int page, int size)` returns a bounded page, and `count()` returns the total. For an existence check, `ParticipantRepository.existsById(UUID)` is available. Administrator controllers also receive `SessionPrincipal` and use `principal.id()` for the administrator UUID; `principal.role()` identifies `ADMIN` or `PARTICIPANT`. The UUID is the internal foreign key; `participantNumber` is a public display number, never an authentication credential. Quiz tables should reference the existing `participants` and `administrator_accounts` as appropriate. Keep dependencies one-way: quiz may use participant services; participant must not depend on quiz.

## 7. Flyway ownership

V1–V6 already exist. F06.1 adds `V7__quiz_foundation.sql`; V8 adds question timing; reserve **V9 through V19** for further Quiz migrations if needed. Do not add empty migrations. Reserve versions before writing them and coordinate all parallel changes with the Tech Lead; V20+ can be reserved for other work after agreement. Flyway ordering matters: applying a higher version before a pending lower Quiz migration can cause the lower migration to be ignored or validation to fail under the current default ordering. Coordinate merge **and deployment order**; do not silently enable out-of-order migrations. Never modify already-applied SQL. F06.0 created no Quiz migration; F06.1 adds V7.

## 8. REST conventions — preparation implemented

All payloads are JSON. Existing API validation uses RFC 9457 problem details (`detail`, sometimes `code`); security failures can use `{status,error}`. Explicitly authorize each route and enforce ADMIN/PARTICIPANT on the server. Quiz commands use REST; WebSocket is not authoritative.

| Proposed operation | Namespace and access |
| --- | --- |
| Create/configure a quiz; manage questions/options | `/api/admin/quiz/**`, ADMIN |
| Start/open/close/advance/finish quiz and retrieve admin results | `/api/admin/quiz/**`, ADMIN |
| Retrieve current quiz and active question | `/api/quiz/**`, authenticated as appropriate |
| Submit an answer; read own score | `/api/participants/me/quiz/**`, PARTICIPANT |
| Retrieve final results and leaderboard | `/api/admin/quiz/leaderboard` (ADMIN); `/api/quiz/me/results` (PARTICIPANT, own result only) |

The administrative preparation endpoints are defined in API_CONTRACT.md; lifecycle endpoints and participant-safe status are described in QUIZ_LIFECYCLE.md; answer endpoints remain proposals. There are no mock Quiz endpoints; F06.2 defines administrator preparation request DTOs. `/quiz`, `/quiz/results`, `/admin/quiz`, `/admin/quiz/results` are proposed frontend routes; none are registered. The existing `BrowserRouter` is in `frontend/src/main.tsx`; coordinate route integration and visual approval before adding screens.

## 9. Quiz behavior and open decisions

The admin controls progression; connected clients synchronize the active question. Only the currently open question accepts an answer; enforce one submission per participant/question with a database uniqueness constraint and transaction-safe validation. Each question has a configured fixed point value: correct receives those points, incorrect zero. Validate correctness and compute scores on the backend. Do not expose correct answers while a question is open or another participant's private answer. Persist results in PostgreSQL and support shared ranking for ties. After reconnect, fetch authoritative state over REST.

Question deadlines, draft-only editing, competition ranks, administrator-only leaderboard, participant-only personal results and answer recovery follow the implemented contracts.

## 10. WebSocket and real-time events

`/ws` uses native STOMP and the HTTP session. Obtain `/api/auth/csrf` after authentication, send the returned header name/token in CONNECT, and subscribe to `/topic/quiz`. `/topic/admin` is ADMIN-only; `/user/queue/notifications` is private to the authenticated namespaced principal. Clients cannot SEND to broker or unapproved app destinations. See [REALTIME_CONTRACT.md](REALTIME_CONTRACT.md) for authorization, origin, heartbeat, transaction and single-instance limitations.

Existing event envelope: `{eventId,type,timestamp,version,payload}`. Available types are `QUIZ_OPENED`, `QUESTION_OPENED`, `QUESTION_CLOSED`, `LEADERBOARD_UPDATED`, `QUIZ_FINISHED`. The **currently implemented** quiz payload is only `RealtimePayload.QuizState(String status)`, not a question DTO. For example, after persisting a future quiz state:

```java
@Transactional
public void openQuiz() {
    // Persist the approved quiz state first.
    realtimeEvents.quiz(EventType.QUIZ_OPENED, new RealtimePayload.QuizState("OPEN"));
}
```

Example current envelope payload: `{"type":"QUIZ_OPENED","payload":{"status":"OPEN"}}` (metadata fields omitted here). Rich question payloads are proposed and require coordination and versioning. Never broadcast leaderboard payloads to participants. Never send early correct answers or private submissions. `RealtimeEvents.quiz` requires a transaction and its F03 listener sends after commit; delivery is best-effort. Reconnect, restore subscriptions and fetch state through REST; deduplicate by event ID if appropriate. F03 uses one backend task with the in-memory simple broker.

## 11. Shared frontend services

`frontend/src/services/api.ts` exports the existing Axios `api`, `ApiError`, and `getCsrfToken()`. It uses relative `/api`, session cookies and a fresh server-provided CSRF header for every state-changing method; it never automatically retries mutations. Errors expose HTTP `status` and problem details. A `session-expired` window event is dispatched on a 401 except an admin-login failure; 403 is returned to the caller and can represent CSRF or authorization failure. Do not store session tokens in localStorage.

```ts
import { api, ApiError } from '../../services/api';
try {
  const result = await api.get<QuizState>('/quiz/current'); // proposed endpoint/type
  // Render result.data.
} catch (error) {
  if (error instanceof ApiError && error.status === 401) { /* request authentication */ }
}
```

The example `QuizState` and endpoint must be defined by the Quiz implementation. `frontend/src/services/realtime.ts` exports a single `realtime` instance. Call `start()` after authentication, unsubscribe feature handlers on unmount and call `stop()` at application logout. Its bounded exponential retry performs up to eight retries, capped at 30 seconds; after exhaustion use `stop()` then `start()` to retry deliberately. Each attempt fetches a fresh CSRF token. `onConnected` fires after initial connection and reconnections; use it to refresh REST state.

```ts
import { realtime } from '../../services/realtime';
const unsubscribe = realtime.subscribe('/topic/quiz', message => {
  const event = JSON.parse(message.body); // validate the approved event schema
  void event;
});
const removeConnectedListener = realtime.onConnected(() => {
  // Reload the authoritative Quiz state via REST.
});
realtime.start();
// In the feature cleanup: unsubscribe(); removeConnectedListener();
// On application logout: await realtime.stop();
```

The service does not expose a publish/SEND method. It uses the browser's existing session cookie, heartbeats and subscription restoration. Avoid parsing untrusted payloads without validation in the future feature.

## 12. Design conventions

Reuse `frontend/src/styles/index.css` and `frontend/tailwind.config.js`: background `#EAE4D6`, surface `#F6F1E4`, ink `#121110`, muted `#4F4A40`, blue `#1565C0`, purple `#8B20D5`, yellow `#E8B020`, red `#C8201F`; Unbounded headings and Outfit body with fallbacks. Existing tokens are implemented; button, card and form components are not yet shared. Coordinate approved designs before building interfaces. Use clear button labels and visible focus, consistent spacing, accessible contrast, semantic form labels, explicit loading/error/disabled states, responsive mobile-first participant layouts, reduced motion, and a simple operational admin layout. Do not invent logos.

## 13. Testing and definition of done

```bash
cd backend && ./mvnw verify
cd ../frontend && npm ci && npm run lint && npm run typecheck && npm run test:shared && npm run build
```

Write Quiz unit, security, PostgreSQL integration/concurrency and WebSocket tests. Verify one-answer uniqueness, open-question/deadline rules once agreed, scoring, ties, admin permissions, CSRF, no early answer disclosure and reconnect recovery. Existing F01–F05 full backend, migrations, session lifecycle and multi-client WebSocket integration were deferred to F07; do not assume earlier tests passed.

Quiz is done when it has persistent configuration, questions and options, admin-controlled lifecycle, synchronized questions, one answer per participant/question, backend validation and fixed-point scoring, persistent results, tie-aware leaderboard, participant and admin interfaces, shared authentication and WebSocket integration, migrations, automated tests and updated API docs. The Tech Lead owns the remaining implementation; F06.1 adds the domain foundation only.

## F06.1 domain foundation

The Tech Lead owns Quiz. See [QUIZ_DOMAIN_MODEL.md](QUIZ_DOMAIN_MODEL.md) for V7, the four JPA entities, integrity and future locking requirements. Administrative preparation, lifecycle, answer admission and results controllers are implemented. F06.1 PostgreSQL execution and JPA validation remain unverified until tested.

## F06.2 administrative preparation

See [API_CONTRACT.md](API_CONTRACT.md) for the implemented ADMIN-only quiz and question routes, required aggregate versions and option validation. Question management is DRAFT-only and transactional. Readiness does not launch a quiz; the lifecycle start revalidates readiness in its transaction. Participant-safe status is implemented; answer endpoints and Quiz event publication remain proposed. Runtime verification against PostgreSQL is outstanding.

## F06.3 lifecycle

ADMIN lifecycle routes and the PARTICIPANT-only `/api/quiz/status` are implemented; see [QUIZ_LIFECYCLE.md](QUIZ_LIFECYCLE.md). The participant DTO exposes no correct-answer flags. Answer admission and fixed scoring are implemented in F06.4; Quiz event publication remains a future milestone. V8 stores closing cutoffs and per-question timings; migration and concurrency behavior remain unverified until tested against PostgreSQL.


F06.4 implements authenticated participant answers, recovery and closed-question feedback, and ADMIN answer counts; see [QUIZ_ANSWER_AND_SCORING.md](QUIZ_ANSWER_AND_SCORING.md). F06.5 adds the administrator leaderboard and personal results; complete Quiz events remain future work.


F06.5 final-result confidentiality: ADMIN alone receives leaderboard and ranks; PARTICIPANT receives only an unranked personal result. See [QUIZ_RESULTS_AND_LEADERBOARD.md](QUIZ_RESULTS_AND_LEADERBOARD.md). A07, A08, P05 and P06 remain pending.
