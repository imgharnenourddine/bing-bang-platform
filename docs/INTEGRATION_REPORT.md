# F07 — Backend integration report

## Scope and environment

Baseline: local commit `4a202c8` on `fix/backend-integration`, plus the uncommitted F07 changes described below. No remote workflow was run. Environment: Linux sandbox, OpenJDK 25.0.5.1 (Java 21 is not installed), Node 24.22.0, Docker Compose v5.6.0. The project targets Java 21 and CI Node 22.14.0; the alternative isolated checks below do not prove compatibility with those exact runtime versions.

## Results

| Area | Status | Evidence / limitation |
| --- | --- | --- |
| Maven wrapper `MAVEN_USER_HOME=/tmp/f07-maven-wrapper ./mvnw clean verify` | BLOCKED | Maven 3.9.9 download failed; sandbox DNS/network unavailable. The default wrapper directory is read-only. |
| System Maven `mvn -o -f backend/pom.xml clean verify` | BLOCKED | Missing cached Spring Security, Spring Session JDBC, Security Messaging, Actuator and Security Test dependencies. No full project compile or Maven tests ran. |
| Selected backend unit compilation/tests | PASSED | `javac --release 21` and JUnit launcher using locally cached, **mixed-version** jars: 23 tests passed, 0 failed, 0 skipped. Includes selected auth, participant, draw, wheel and transactional-event unit tests. Not a substitute for Maven/Java 21 or real integration. A pre-existing ambiguous Mockito invocation was corrected to compile these tests. |
| Backend startup / health / session restart | BLOCKED | Docker daemon socket denied; backend dependencies unavailable. |
| Flyway V1–V6 on fresh or upgraded PostgreSQL | BLOCKED | No accessible Docker daemon or local PostgreSQL server. SQL and migrations were inspected; no applied migration was edited. No schema execution or Hibernate validation is claimed. |
| Authentication, CSRF, JDBC sessions and participant registration over HTTP | NOT EXECUTED | Requires running backend/PostgreSQL. The isolated smoke test is provided but not run. Existing 23 unit tests do not establish the full HTTP or JDBC lifecycle. |
| WebSocket authentication, authorization, private isolation and logout | NOT EXECUTED | Requires real backend and sessions. F03 simple broker remains single-instance. |
| Random draw, reset and prize wheel end-to-end | NOT EXECUTED | Unit tests above cover selected logic only; PostgreSQL locks, constraints, rollback, retry and persistence need real integration execution. |
| Frontend existing installation | PASSED | `npm run lint`, `npm run typecheck`, `npm run test:shared` (2 tests passed), `npm run build` on Node 24. |
| Clean `npm ci` in `/tmp/f07-npm-ci` | BLOCKED | `npm ci --offline` failed in esbuild postinstall with `spawnSync ... EPERM`; isolated `npm ci --offline --ignore-scripts` succeeded, but this is not a full clean installation. Existing project `node_modules` was not removed. |
| Compose config | PASSED | `docker compose --env-file .env.example config -q` and the same command with `-f compose.yaml -f compose.local.yaml`. |
| Container builds / PostgreSQL, Flyway, backend and Nginx health | BLOCKED | `docker info`: permission denied accessing Docker socket. Healthcheck/proxy changes below are unverified in live containers. |
| Performance at 100 users | NOT EXECUTED | No running isolated environment. No capacity claim is made. |
| Quiz integration | NOT EXECUTED | Mohammed's Quiz is not integrated. V7–V19 remain reserved; preserve F06.0 contracts and coordinate deployment ordering. |

## Defects and corrections

1. **Verified source/test defect:** `RealtimeTransactionTest` had an ambiguous Mockito `convertAndSend` overload. A typed `Object` matcher resolves compilation in the cached-classpath check; the transactional-event tests were among the 23 passing tests.
2. **Verified configuration defect:** the CI containers job starts a new database but did not provide administrator bootstrap credentials. The workflow now sets disposable CI-only credentials; it still needs a real CI run.
3. **Healthcheck mitigation, not root-cause proof:** Nginx listens on internal port 80. Its old healthcheck used `localhost`; the previously reported connection refusal could involve address resolution, but that cause has not been verified. A lightweight exact `/healthz` endpoint and `127.0.0.1:80` probe remove that ambiguity. The check is not disabled. Live container health remains BLOCKED.
4. **Proxy route correction:** `/api/` was proxied, but exact `/api` could fall through to SPA HTML. It now proxies to the backend. `/ws` upgrade configuration and Vite `/api`/`/ws` proxy were inspected; live proxy, session-cookie and WebSocket upgrade validation remains BLOCKED.
5. CI cleanup is now a separate `always()` step so a log collection failure cannot skip cleanup. `down -v` is used only by disposable GitHub-hosted CI, not on a user's development project. The backend job now starts a disposable PostgreSQL 16 service and sets the F02/F04/F05 test variables so their opt-in concurrency tests can run; this workflow change has not been run remotely or locally here.

The login limiter keys on the direct peer address. Behind Nginx, clients can share the proxy's bucket; this is a known operational limitation, not a reason to trust arbitrary forwarded headers. Concurrent same-session participant registration and fixation/session persistence remain unverified with JDBC.

No P0 end-to-end readiness or 100-user capacity claim is justified. User-provided reports of previous GitHub Actions failures were not independently verified here; no remote access was attempted.

## Commands executed

- `MAVEN_USER_HOME=/tmp/f07-maven-wrapper timeout 20 ./mvnw clean verify` (from `backend/`): BLOCKED download.
- `mvn -o -f backend/pom.xml clean verify`: BLOCKED missing dependencies.
- A selective `javac --release 21 -proc:none` compile with cached dependencies and a local JUnit Platform runner: 23/23 passed; cache contains multiple library versions. This was not the Maven build.
- `npm run lint`, `npm run typecheck`, `npm run test:shared`, `npm run build` from `frontend/`: PASSED on the existing installation; shared tests 2/2 passed.
- In a disposable `/tmp/f07-npm-ci` directory, `npm ci --offline --ignore-scripts --cache /home/noureddine/.npm --no-audit --no-fund`: PASSED with scripts disabled; `npm ci --offline --cache /home/noureddine/.npm --no-audit --no-fund`: BLOCKED at esbuild postinstall with `EPERM`.
- Both Compose configuration checks, `git diff --check`, Python syntax check and non-isolated safety-gate check of `tests/integration/smoke.py`: PASSED. Static Nginx/Compose port, health-path and `/api` route consistency check: PASSED, without running Nginx. Docker socket access: BLOCKED.

## Safe manual validation by the Tech Lead

Run from the project root on a machine with Java 21, Node 22.14.0, network access and Docker permissions. This creates a **new uniquely named** Compose project and volume; it does not touch the existing project. Check ports 18080 and 18088 are unused first. The credentials below are disposable test credentials, never production credentials.

```bash
cd backend
./mvnw clean verify
cd ../frontend
npm ci
npm run lint && npm run typecheck && npm run test:shared && npm run build
cd ..

export F07_COMPOSE_PROJECT="f07-$(date +%s)"
export POSTGRES_DB=f07db POSTGRES_USER=f07user POSTGRES_PASSWORD=f07-only-db-password
export ADMIN_USERNAME=f07-admin ADMIN_PASSWORD=f07-only-admin-password
export F07_ADMIN_USERNAME="$ADMIN_USERNAME" F07_ADMIN_PASSWORD="$ADMIN_PASSWORD"
export APP_PORT=18080 WEB_PORT=18088 SESSION_COOKIE_SECURE=false
export WS_ALLOWED_ORIGINS=http://localhost:18088

docker compose -p "$F07_COMPOSE_PROJECT" config -q
docker compose -p "$F07_COMPOSE_PROJECT" up --build -d --wait --wait-timeout 180
docker compose -p "$F07_COMPOSE_PROJECT" ps
F07_ISOLATED=1 F07_RESTART_BACKEND=1 python3 tests/integration/smoke.py
docker compose -p "$F07_COMPOSE_PROJECT" logs --no-color
# Stops this isolated project and preserves its volume:
docker compose -p "$F07_COMPOSE_PROJECT" down
```

The smoke script refuses to run without `F07_ISOLATED=1`, a unique `f07-*` project name, the local port 18088 and disposable credentials. It also refuses a database with existing participants or prizes. It creates test data and must **never** target a development or event database. It checks admin authentication, three participant sessions, private STOMP notifications, draw and spin, stock, idempotency, cancellation, reset, history and optional backend-restart persistence. The script and healthcheck correction are **not validated end to end** in this sandbox. Test sessions, F03 authorization denial, parallel HTTP registrations, and cancellation/spin/reset and stock/config races still need the PostgreSQL and multi-client tests in a full integration environment. The script uses a brief subscription wait because Spring's simple broker does not guarantee STOMP receipts, so it is not a replacement for robust asynchronous delivery tests.

The isolated project volume is intentionally retained by `down`; arrange explicit cleanup of that **new disposable** volume separately. Do not execute `down -v` against an existing development or event project. After pushing, the Tech Lead must verify the actual GitHub Actions result separately.
