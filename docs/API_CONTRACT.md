# Shared API contract

These are planned conventions, not implemented business endpoints. The platform and Mohammed's quiz module must share authentication, participant identity and WebSocket infrastructure; the quiz module is reserved for Mohammed.

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
| Authentication | JWT proposed, pending Tech Lead approval |
| Timestamps | UTC, ISO 8601 |
| Payload | JSON |
| SQL names | snake_case |
| Java and TypeScript names | camelCase |

No token issuance, authentication flows or WebSocket broker are configured in F00. Error responses for handled validation failures use RFC 9457 problem details. Spring Security currently denies all requests except actuator health probes.
