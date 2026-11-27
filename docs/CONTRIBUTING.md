# Contributing

Inspect existing work and shared contracts before changing a feature. Coordinate changes to common identity, security, event contracts and real-time communication. Mohammed owns the quiz; obtain authorization before changing it. Preserve approved designs and avoid speculative business functionality.

Flyway migrations live in `database/migrations` and run once, before the application. Never edit a migration once applied, including V1. Before writing a migration, coordinate and reserve the next unique version with both developers; use `V<number>__description.sql`. Commit conflicting allocations only after resolving them before either is applied. Use a new forward migration to correct deployed changes.

Run backend `./mvnw verify` and frontend `npm ci`, `npm run lint`, `npm run typecheck`, `npm run build`; run Compose integration when Docker is available. Agents may create local feature branches and commits when requested. The Tech Lead handles push and other remote operations.

## Parallel Quiz work

After `chore/quiz-integration-setup` is merged, Mohammed works on `feature/quiz`; the Tech Lead works on `fix/backend-integration`, then `feature/participant-ui` and `feature/admin-dashboard`. No direct commits to main. Use focused commits, pull requests and coordination for shared files and contracts. Never force-push shared branches without agreement. Agents still do not perform remote Git actions.

V1–V6 exist; V7–V19 are reserved for Quiz. Agree on later versions for other work, and coordinate deployment order: default Flyway ordering can reject or ignore a newly added lower version after a higher version has already been applied. Never edit applied migrations or introduce empty ones. See [Quiz integration](QUIZ_INTEGRATION.md) for setup, ownership and test expectations.
