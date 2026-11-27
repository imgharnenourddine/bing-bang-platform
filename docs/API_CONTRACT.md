# Shared API contract

Authentication and participant endpoints below are implemented; other business endpoints remain planned. WebSocket infrastructure is described in [REALTIME_CONTRACT.md](REALTIME_CONTRACT.md). The platform and Mohammed's quiz module must share authentication, participant identity and WebSocket infrastructure; the quiz module is reserved for Mohammed.

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
