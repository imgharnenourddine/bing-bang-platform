# Contributing

Inspect existing work and shared contracts before changing a feature. Coordinate changes to common identity, security, event contracts and real-time communication. Mohammed owns the quiz; obtain authorization before changing it. Preserve approved designs and avoid speculative business functionality.

Flyway migrations live in `database/migrations` and run once, before the application. Never edit a migration once applied, including V1. Before writing a migration, coordinate and reserve the next unique version with both developers; use `V<number>__description.sql`. Commit conflicting allocations only after resolving them before either is applied. Use a new forward migration to correct deployed changes.

Run backend `./mvnw verify` and frontend `npm ci`, `npm run lint`, `npm run typecheck`, `npm run build`; run Compose integration when Docker is available. Agents do not perform Git write operations; the Tech Lead manages Git.
