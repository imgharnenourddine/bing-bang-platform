# Architecture

A modular monolith hosts one Spring Boot application and one PostgreSQL database; the React client uses same-origin `/api` and future `/ws` via Nginx. PostgreSQL is authoritative. Docker Compose waits for database health, a successful dedicated Flyway migration and backend readiness, in that order. Actuator exposes health probes; JPA validates instead of creating schema. Flyway is not an application dependency and runs only in the migration container.

Planned backend features: `auth` (identity and access), `event` (event state), `participant` (participant identity), `draw` (selection), `wheel` (prizes), `quiz` (Mohammed's ownership), `notification` (notifications); `common` holds shared errors, `config` infrastructure configuration. These are boundaries, not implemented services. Planned frontend boundaries are auth, participant, admin, draw, wheel, quiz; shared UI, layouts, hooks, services, styles, assets and types support them. Empty feature folders are not tracked until they contain work.

Security currently denies all but health endpoints. The OAuth2 resource-server dependency is present, but JWT verification requires an approved issuer and explicit security configuration. No authentication or WebSocket endpoint exists yet. Production deployments must supply proper TLS, secret management and an approved auth design.
