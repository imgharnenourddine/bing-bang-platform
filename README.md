# BING BANG — Kick-off Platform

![Status](https://img.shields.io/badge/status-in%20development-orange)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-Backend-6DB33F)
![React](https://img.shields.io/badge/React-TypeScript-61DAFB)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-4169E1)

The interactive event platform for **BING BANG**, a collaborative kick-off event organized by **GDG, AWS Club and N7Geeks** at **ENSET Mohammedia**.

> **Project status:** F01 and F02 add backend administrator authentication and participant registration. Other business features below are planned.

## Overview

BING BANG Platform centralizes participant registration, digital identification, randomized giveaways, weighted prize distribution and synchronized quizzes within a single real-time web application.

The platform provides a mobile-first participant experience, a secure administration dashboard and a proposed read-only projection interface for the event venue.

The initial MVP targets approximately 100 participants, with a one-day development window and a team of two developers.

## Features

### Participant Registration

- Scan a shared event QR code and enter first and last names.
- Receive a unique sequential participant number upon successful registration.
- Retain the assigned identity through a secure browser session.
- Access a personal digital event pass and receive selection notifications.
- Join the quiz without registering again.

### Administration Dashboard

- Secure administrator authentication and role-based access control.
- View registered participants and manage registration availability.
- Trigger random participant selection and prize spins.
- Configure gifts, stock, weights and premium growth factors.
- Create and edit quiz questions before opening the quiz.
- Control quiz progression and view results.

### Random Participant Selection

Participants are selected uniformly at random from the eligible pool. Each participant may be selected only once during the event. A selected participant receives an in-app notification in real time. Administrators must resolve the active selection by completing its spin or marking the participant absent before starting another draw.

### Weighted Spinning Wheel

Only administrators can trigger a spin for a selected participant. The backend determines the winning gift; the frontend animates the wheel toward that result.

Each gift has an initial weight, available stock, premium status and a configurable growth factor. Premium gift weights increase geometrically with the number of completed spins since the last premium win:

```text
Standard weight: w(k) = w0
Premium weight:  w(k) = w0 * min(r^k, M)
Probability:     P(i) = w(i) / sum(available weights)
```

Here, `k` is the number of consecutive non-premium spins, `r >= 1` is the growth factor, and `M >= 1` is the maximum multiplier. A standard win increments the counter; a premium win resets it. Exhausted and inactive gifts are excluded. Stock updates, result persistence and counter updates are atomic. Each draw permits at most one spin. The weighted nature of the wheel must be communicated to participants.

### Synchronized Quiz

- Administrator-managed single-answer multiple-choice questions.
- Quiz locked until explicitly opened by an administrator.
- Questions displayed simultaneously to connected participants.
- Server-controlled deadlines and question progression.
- One answer per participant per question.
- Correct answers withheld until the question closes.
- Automatic scoring and final leaderboard, with shared ranks for equal scores.

### Real-Time Communication

WebSocket/STOMP broadcasts committed state changes. REST endpoints provide authoritative state and personalized data. Clients resynchronize on reconnect, with polling as a proposed fallback when WebSocket is unavailable.

## Technology Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot, Maven |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL |
| Security | Spring Security, PostgreSQL-backed HTTP sessions, BCrypt and CSRF |
| Real-time | Spring WebSocket, STOMP |
| Database migrations | Flyway |
| Frontend | React, TypeScript, Vite |
| Styling | Tailwind CSS and shared design tokens |
| Testing | JUnit, Mockito, integration and concurrency tests |
| Infrastructure | Docker, Docker Compose, HTTPS reverse proxy |

## Architecture

The application follows a modular monolith architecture. All modules share a single backend, frontend and PostgreSQL database. PostgreSQL is the source of truth; REST handles commands and queries, while WebSocket broadcasts state changes after transactions commit.

```mermaid
flowchart TD
    P[Participant Mobile Interface] --> UI[React Application]
    A[Admin Dashboard] --> UI
    D[Projection Interface] --> UI
    UI -->|REST API| B[Spring Boot]
    B -->|WebSocket / STOMP| UI
    subgraph Backend[Backend Modules]
        B --> C[Authentication and Registration]
        B --> L[Random Draw and Spinning Wheel]
        B --> Q[Quiz and Leaderboard]
    end
    C --> DB[(PostgreSQL)]
    L --> DB
    Q --> DB
```

## Repository Structure

```text
bing-bang-platform/
├── backend/
│   └── src/main/java/ma/enset/bigbang/
│       ├── auth/
│       ├── config/
│       ├── common/
│       ├── event/
│       ├── participant/
│       ├── draw/
│       ├── wheel/
│       ├── notification/
│       └── quiz/
├── frontend/
│   └── src/
│       ├── api/
│       ├── components/
│       ├── contexts/
│       ├── hooks/
│       ├── features/
│       └── pages/
├── docs/
├── docker-compose.yml
├── .env.example
├── .gitignore
└── README.md
```

This is the planned structure, not an inventory of implemented files.

## Shared Development Contracts

- Participant internal identifier: UUID; public participant number: integer.
- Roles: `ADMIN` and `PARTICIPANT`.
- REST API prefix: `/api`; WebSocket endpoint: `/ws`.
- Shared participant identity, security configuration and WebSocket infrastructure.
- JSON request and response bodies; UTC timestamps in ISO 8601 format.
- Database migrations coordinated between developers to avoid duplicate versions.
- Server-side validation and transactional handling of critical operations.
- React route guards complement, but never replace, backend authorization.

## Security and Data Integrity

- Sequential numbering must be safe under concurrent registration.
- Unique database constraints prevent duplicate participant numbers, selections, spins and quiz answers.
- Administrators authenticate using securely hashed passwords.
- Session cookies, CSRF protection and HTTPS protect authenticated operations.
- The public participant number is not an authentication credential.
- The server validates quiz deadlines, gift availability and all state transitions.
- Personal data is restricted to authorized users; public displays use participant numbers.
- Secrets and database credentials must never be committed to Git.

A browser session alone cannot guarantee that the same individual will not register from another device. Registration supervision is therefore required for the MVP.

## Design System

The frontend must follow the existing official BING BANG event identity. Logos, typography, colors, backgrounds and reusable UI components will be defined from the supplied event assets and shared by both developers. No replacement branding is assumed.

## Development Team

| Developer | Role | Responsibilities |
| --- | --- | --- |
| Noureddine Imgharn | Tech Lead — Core Platform | Architecture, security, registration, participant management, random draw, spinning wheel, shared real-time infrastructure and deployment |
| Mohammed | Co-Tech Lead — Quiz Module | Quiz data model, API, question management, synchronization, answer submission, scoring, leaderboard and quiz interfaces |

Both developers contribute to integration, testing and release preparation.

## Git Workflow

- `main` — stable, deployable branch.
- `feature/event-core` — core platform development.
- `feature/quiz` — quiz module development.

Use pull requests to integrate features and coordinate changes to shared contracts. Follow Conventional Commits, for example:

```text
feat(registration): implement sequential participant numbering
feat(quiz): implement synchronized question progression
fix(wheel): prevent duplicate prize allocation
```

## Getting Started

**Foundation setup is incomplete:** `frontend/package-lock.json` could not be generated in the restricted environment. Run `npm install` in `frontend/` with registry access and review the resulting lockfile before using `npm ci`, building the frontend image, or running CI.

Prerequisites: Docker with Compose, or Java 21, Maven Wrapper, Node 22.14.0 and PostgreSQL for local development. Copy `.env.example` to `.env` and replace the local database password. The Compose database is not exposed on the host; the frontend and backend bind to localhost.

```bash
cp .env.example .env
# Edit .env and replace the password before starting.
docker compose up --build -d
docker compose ps
docker compose down
```

Home: http://localhost:8088/; backend health: http://localhost:8080/actuator/health; readiness: http://localhost:8080/actuator/health/readiness. Ports can be changed in `.env`. `docker compose down` preserves the database volume.

```bash
cd backend && ./mvnw verify
cd ../frontend && npm ci && npm run lint && npm run typecheck && npm run build
```

For backend development outside Compose, run PostgreSQL and the Flyway migrations first, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, then run `./mvnw spring-boot:run`. Vite's `npm run dev` proxies `/api` and `/ws` to localhost:8080. See [architecture](docs/ARCHITECTURE.md), [contracts](docs/API_CONTRACT.md), [design](docs/DESIGN_SYSTEM.md), and [contributing](docs/CONTRIBUTING.md).

## MVP Acceptance Criteria

- 100 concurrent registrations without duplicate numbers.
- Participant identity retained on page refresh.
- No repeated participant selection or duplicate prize allocation.
- Correct stock handling and premium probability reset.
- Quiz inaccessible before opening; duplicate and late answers rejected.
- Connected clients synchronize question changes and recover after reconnecting.
- Unauthorized administrative requests rejected.
- Full event rehearsal completed before release.

## Project Status

**F02 — Participant registration in development.**

The application foundation is being initialized. Business features, deployment and performance validation are pending.

---

**BING BANG** · GDG × AWS Club × N7Geeks · ENSET Mohammedia

## Administrator authentication (F01)

Authentication uses PostgreSQL-backed HTTP sessions rather than JWT. Before first startup, set both `ADMIN_USERNAME` and `ADMIN_PASSWORD` in your local `.env`; use a unique username and a strong password of 12–72 UTF-8 bytes. The password is hashed with BCrypt and is never overwritten by later starts. After bootstrap, remove these variables from `.env`. Concurrent startup is serialized in PostgreSQL. The migration container must run before the backend.

`SESSION_TIMEOUT` defaults to `30m`; `LOGIN_MAX_ATTEMPTS` to `5`; `LOGIN_WINDOW` to `15m`. `SESSION_COOKIE_SECURE` controls the development cookie; use HTTPS and the `prod` profile in production, which forces secure cookies. Cookies are HttpOnly and SameSite=Lax. The sample `.env` sets secure cookies off for local HTTP only. Never commit real credentials. The in-memory limiter counts attempts per direct peer address and is not shared across instances; reverse proxies can cause clients to share one bucket. Revisit this before multi-instance deployment.

For manual testing, with `jq` installed, start PostgreSQL, Flyway, and the backend (`docker compose up --build -d postgres flyway backend`), then obtain a CSRF token before each state-changing operation. Enter credentials interactively to avoid shell history:

```bash
read -r -p 'Admin username: ' ADMIN_LOGIN
read -r -s -p 'Admin password: ' ADMIN_SECRET; echo
TOKEN=$(curl -sS -c /tmp/bigbang-cookies http://localhost:8080/api/auth/csrf | jq -r .token)
jq -n --arg username "$ADMIN_LOGIN" --arg password "$ADMIN_SECRET" '{username:$username,password:$password}' |
  curl -sS -b /tmp/bigbang-cookies -c /tmp/bigbang-cookies -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $TOKEN" --data-binary @- http://localhost:8080/api/auth/admin/login
unset ADMIN_SECRET
curl -sS -b /tmp/bigbang-cookies http://localhost:8080/api/auth/me
TOKEN=$(curl -sS -b /tmp/bigbang-cookies -c /tmp/bigbang-cookies http://localhost:8080/api/auth/csrf | jq -r .token)
curl -i -b /tmp/bigbang-cookies -c /tmp/bigbang-cookies -X POST -H "X-CSRF-TOKEN: $TOKEN" http://localhost:8080/api/auth/logout
curl -i -b /tmp/bigbang-cookies http://localhost:8080/api/auth/me
rm -f /tmp/bigbang-cookies
```

See [API contracts](docs/API_CONTRACT.md) for response codes. CSRF is required for login and logout, and a fresh token is needed after each. No participant registration or quiz functionality is included.


## Participant registration (F02)

Registration accepts first and last names only. A PostgreSQL sequence assigns a positive unique number; rollbacks can cause gaps. A successful registration authenticates the participant in the existing JDBC-backed session. Repeated registration in the same established session returns the existing participant. Identical names are allowed. A new browser or lost session cannot be linked reliably to the same person.

With the backend running and `jq` installed, a minimal local test is:

```bash
TOKEN=$(curl -sS -c /tmp/bigbang-participant-cookies http://localhost:8080/api/auth/csrf | jq -r .token)
curl -sS -b /tmp/bigbang-participant-cookies -c /tmp/bigbang-participant-cookies \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $TOKEN" \
  -d '{"firstName":"Ahmed","lastName":"Alami"}' http://localhost:8080/api/participants/register
curl -sS -b /tmp/bigbang-participant-cookies http://localhost:8080/api/participants/me
curl -sS -b /tmp/bigbang-participant-cookies http://localhost:8080/api/auth/me
# Fetch a fresh CSRF token after registration before further POST requests.
rm -f /tmp/bigbang-participant-cookies
```

Administrators can use `GET /api/admin/participants?page=0&size=20` and `GET /api/admin/participants/count` with an authenticated admin session. See [API contracts](docs/API_CONTRACT.md) for statuses and response shapes.
