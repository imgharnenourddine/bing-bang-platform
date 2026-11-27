# Agent instructions — THE BIG BANG

THE BIG BANG is an interactive event platform for N7Geeks, GDG and AWS Cloud Clubs at ENSET Mohammedia. Inspect existing code, documentation, and changes first; work exclusively in the provided local project directory. Implement only the requested feature, avoid unnecessary refactoring, preserve approved designs and module contracts, and never invent business requirements or build an unapproved interface.

Use Java 21, Spring Boot and Maven; PostgreSQL with dedicated Flyway migrations; React, strict TypeScript, Vite and Tailwind; Docker and Compose. Future infrastructure uses Terraform and AWS ECS/ECR. Maintain the modular monolith and feature-based frontend. Mohammed owns the quiz module: never modify it without authorization. Reuse shared identity, authentication and real-time infrastructure.

Deny access by default, validate inputs, never store or expose secrets, and do not introduce placeholder authentication. JWT is proposed, not approved or implemented. Keep the database as source of truth. Add immutable versioned Flyway migrations; coordinate version ownership with the other developer before allocating a version. Never rewrite applied migrations.

Follow docs/DESIGN_SYSTEM.md and existing official assets; do not invent logos. Use responsive mobile-first, accessible interfaces and reduced motion. Never provision cloud infrastructure without explicit approval.

Run relevant tests, lint, type checks and builds; report failures and unavailable checks honestly. Update documentation and contracts when needed. Provide a complete implementation report after every task.

All Git and GitHub operations belong to the Tech Lead. Never create or switch branches; never stage, commit, push, pull, fetch, reset, rebase or merge; never connect to a remote Git repository or create or merge pull requests. Leave changes unstaged and uncommitted. Local read-only Git inspection is allowed.
