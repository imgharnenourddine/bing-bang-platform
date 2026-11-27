# Agent instructions — THE BIG BANG

THE BIG BANG is an interactive event platform for N7Geeks, GDG and AWS Cloud Clubs at ENSET Mohammedia. Inspect existing code, documentation, and changes first; work exclusively in the provided local project directory. Implement only the requested feature, avoid unnecessary refactoring, preserve approved designs and module contracts, and never invent business requirements or build an unapproved interface.

Use Java 21, Spring Boot and Maven; PostgreSQL with dedicated Flyway migrations; React, strict TypeScript, Vite and Tailwind; Docker and Compose. Future infrastructure uses Terraform and AWS ECS/ECR. Maintain the modular monolith and feature-based frontend. Mohammed owns the quiz module: never modify it without authorization. Reuse shared identity, authentication and real-time infrastructure.

Deny access by default, validate inputs, never store or expose secrets, and do not introduce placeholder authentication. Use Spring Security with PostgreSQL-backed Spring Session JDBC, BCrypt, RBAC, and CSRF; JWT is not the selected authentication strategy. Keep the database as source of truth. Add immutable versioned Flyway migrations; coordinate version ownership with the other developer before allocating a version. Never rewrite applied migrations.

Follow docs/DESIGN_SYSTEM.md and existing official assets; do not invent logos. Use responsive mobile-first, accessible interfaces and reduced motion. Never provision cloud infrastructure without explicit approval.

Run relevant tests, lint, type checks and builds; report failures and unavailable checks honestly. Update documentation and contracts when needed. Provide a complete implementation report after every task.

Follow the Backend First strategy. Agents may inspect Git, create or switch a task-specific local branch, stage relevant files and create local commits when requested. Never push, pull, fetch, reset, rebase, merge, connect to remote repositories or create pull requests; the Tech Lead handles remote operations. Preserve existing work.

Participant registration uses the shared JDBC-backed session and `PARTICIPANT` role. PostgreSQL owns sequential participant numbering; do not infer identity from the public number or assume separate browser sessions belong to different people. Future modules should use the participant service and shared contracts, without modifying the quiz module without authorization.

Real-time clients use authenticated STOMP over /ws and must send CSRF on CONNECT. Keep subscriptions deny-by-default, use role-prefixed UUID identities for private delivery, publish typed events only after commit, and do not leak personal information to shared topics. PostgreSQL and REST remain authoritative. The simple broker is single-instance; consult docs/REALTIME_CONTRACT.md before changing contracts.

Draw operations must acquire the database draw_control row lock, preserve history and eligibility by cycle, and use server-side uniform randomness. Cancellation does not restore eligibility. Future prize allocation must lock the active draw first, enforce one award per draw in the database, and complete the draw in the same transaction. Never expose draw completion as a standalone admin command.

Prize allocation must lock draw_control, then wheel_configuration, then inventory rows in UUID order. Award uniqueness by draw, stock decrement, progression and draw completion belong to the same transaction. Preserve award and segment snapshots, audit manual stock adjustments, and never reset wheel progression when draw cycles reset. See docs/PRIZE_WHEEL.md.

Quiz ownership: Mohammed owns `backend/src/main/java/ma/enset/bigbang/quiz/`, `frontend/src/features/quiz/`, Quiz-specific tests and documentation, and reserved Flyway versions V7–V19. The Tech Lead owns shared authentication, participants, messaging, draw, wheel, frontend infrastructure, general interfaces, deployment and cross-module integration. Coordinate edits to SecurityConfig, WebSocket config, event types, Maven/npm dependencies and lockfile, routing, design tokens, Compose, CI and this file. Higher Flyway migrations can block pending lower versions under default ordering: coordinate version allocation, merge and deployment order. See docs/QUIZ_INTEGRATION.md. Quiz endpoints and screens must not be exposed before their authorization and design are approved.

F07 status: backend Maven, PostgreSQL/Flyway, authenticated HTTP and real WebSocket integration remain blocked in the restricted environment; do not claim the platform is production-ready. See docs/INTEGRATION_REPORT.md. Quiz remains Mohammed’s separate integration task. Use isolated disposable databases for integration tests and never delete existing volumes.

P01 participant registration is implemented at `/`, with the P02 home at `/participant`. Reuse `frontend/src/features/participant` and its shared authentication context and API service; preserve participant and administrator session boundaries. The official poster crops are in `frontend/src/assets/participant`. Dedicated participant draw and wheel screens are cancelled; Quiz screens remain future work. Browser and live backend integration still require verification in a running environment.

A01 administrator login lives at `/admin/login`; `/admin` is the role-protected A02 dashboard. Reuse the existing session context and shared CSRF-aware API client. Participant sessions require explicit logout before administrator login. Live backend and browser verification remains outstanding; see README. Do not commit the unrelated reconciliation report with A01.

A02 uses the reusable administrator layout and shared API/STOMP services. Its dashboard shows authoritative REST counts and draw state; management interfaces beyond the dashboard remain future work. See README for live-validation limits.

A03 Lucky Draw management is at `/admin/draw`, protected by the existing ADMIN guard and using the A02 layout and official logo header. Cancellation consumes eligibility for the cycle; reset starts a new cycle and preserves draws, awards, inventory and premium progression. Never add participant draw or wheel screens. Live browser/backend validation remains required.
