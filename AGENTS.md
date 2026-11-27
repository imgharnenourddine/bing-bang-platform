# Agent instructions — THE BIG BANG

THE BIG BANG is an interactive event platform for N7Geeks, GDG and AWS Cloud Clubs at ENSET Mohammedia. Inspect existing code, documentation, and changes first; work exclusively in the provided local project directory. Implement only the requested feature, avoid unnecessary refactoring, preserve approved designs and module contracts, and never invent business requirements or build an unapproved interface.

Use Java 21, Spring Boot and Maven; PostgreSQL with dedicated Flyway migrations; React, strict TypeScript, Vite and Tailwind; Docker and Compose. Future infrastructure uses Terraform and AWS ECS/ECR. Maintain the modular monolith and feature-based frontend. Mohammed owns the quiz module: never modify it without authorization. Reuse shared identity, authentication and real-time infrastructure.

Deny access by default, validate inputs, never store or expose secrets, and do not introduce placeholder authentication. Use Spring Security with PostgreSQL-backed Spring Session JDBC, BCrypt, RBAC, and CSRF; JWT is not the selected authentication strategy. Keep the database as source of truth. Add immutable versioned Flyway migrations; coordinate version ownership with the other developer before allocating a version. Never rewrite applied migrations.

Follow docs/DESIGN_SYSTEM.md and existing official assets; do not invent logos. Use responsive mobile-first, accessible interfaces and reduced motion. Never provision cloud infrastructure without explicit approval.

Run relevant tests, lint, type checks and builds; report failures and unavailable checks honestly. Update documentation and contracts when needed. Provide a complete implementation report after every task.

Follow the Backend First strategy. Agents may inspect Git, create or switch a task-specific local branch, stage relevant files and create local commits when requested. Never push, pull, fetch, reset, rebase, merge, connect to remote repositories or create pull requests; the Tech Lead handles remote operations. Preserve existing work.

Participant registration uses the shared JDBC-backed session and `PARTICIPANT` role. PostgreSQL owns sequential participant numbering; do not infer identity from the public number or assume separate browser sessions belong to different people. Future modules should use the participant service and shared contracts, without modifying the quiz module without authorization.

Real-time clients use authenticated STOMP over /ws and must send CSRF on CONNECT. Keep subscriptions deny-by-default, use role-prefixed UUID identities for private delivery, publish typed events only after commit, and do not leak personal information to shared topics. PostgreSQL and REST remain authoritative. The simple broker is single-instance; consult docs/REALTIME_CONTRACT.md before changing contracts.
