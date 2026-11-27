# Shared API contract

Authentication endpoints below are implemented; all business endpoints and WebSocket contracts remain planned. The platform and Mohammed's quiz module must share authentication, participant identity and WebSocket infrastructure; the quiz module is reserved for Mohammed.

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

Get a CSRF token before login, send it in the returned header, and obtain a new token after login and logout. Missing or invalid tokens return 403. Validation failures return 400 problem details. Security failures return JSON with `status` and `error`. Other routes are denied by default. The quiz module must reuse these shared session and identity contracts. There is no participant authentication or WebSocket implementation yet.
