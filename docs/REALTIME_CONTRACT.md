# Real-time contract

F03 provides backend infrastructure and F04 adds the draw business flow. Wheel and quiz event names below remain reserved contracts. PostgreSQL remains authoritative; business commands use REST. WebSocket delivery is best-effort and has no replay or guaranteed ordering. An event may be lost after a successful database commit. No notification history or outbox is implemented.

## Connect and authenticate

1. Authenticate as an administrator or register a participant through the existing HTTP APIs. Keep the session cookie; do not expose it to JavaScript.
2. GET `/api/auth/csrf` after authentication and retain the returned `headerName` and `token`. The prior token is invalidated during authentication.
3. Open a **native WebSocket** at `/ws` on the same origin (HTTPS deployments use `wss://`). Send a STOMP `CONNECT` frame with that header and token. Spring Security checks CSRF on CONNECT. No SockJS or JWT is used.
4. Subscribe only to authorized destinations below. Session expiry, logout, or a changed HTTP identity prevents further deliveries and closes the socket on the periodic check. Disconnection itself does not log a user out or delete a participant.

The browser sends the HttpOnly `SESSION` cookie with the handshake. Exact allowed origins come from `WS_ALLOWED_ORIGINS` (default `http://localhost:8088`); configure your actual HTTPS origin in production. Wildcards are rejected. STOMP principals use `ADMIN:<uuid>` or `PARTICIPANT:<uuid>`; the UUID and role come from the authenticated HTTP session, never from STOMP client headers. The prefixes avoid cross-role collisions.

| Subscription | ADMIN | PARTICIPANT | Contents |
| --- | --- | --- | --- |
| `/topic/event` | yes | yes | public event state |
| `/topic/draw` | yes | yes | public draw updates |
| `/topic/wheel` | yes | yes | public wheel updates |
| `/topic/quiz` | yes | yes | public quiz state, no early answers |
| `/topic/admin` | yes | no | administrator-only notices |
| `/user/queue/notifications` | yes | yes | own private notices |

All other subscriptions are denied. No application SEND destinations are approved: clients cannot send commands to `/app`, `/topic`, `/queue`, or `/user`. Direct broker queues and other users' queues are not subscribable. The server sends private participant messages to the role-namespaced UUID; Spring routes them to all active sessions for that identity. A subscription address does not let a client pick the recipient.

## Envelope and types

```json
{"eventId":"1bc100aa-99b9-491b-b84b-d458d0b5b199","type":"DRAW_STARTED","timestamp":"2026-11-27T19:00:00Z","version":1,"payload":{"drawId":"18d5eefb-e9cf-4bf3-bcdf-fc98f01e8a61","participantNumber":null}}
```

`eventId` is a unique UUID, `timestamp` an ISO 8601 UTC instant, and `version` currently 1. Payloads are explicit Java records and never JPA entities. `EVENT_STATE_UPDATED` uses `EventState`; `PARTICIPANT_REGISTERED` uses `ParticipantRegistered` and is sent **only** to `/topic/admin`. `DRAW_STARTED`, `DRAW_PARTICIPANT_SELECTED`, `DRAW_COMPLETED` use `DrawState`; `WHEEL_STARTED`, `WHEEL_RESULT`, `PRIZE_AWARDED` use `WheelState`; `QUIZ_OPENED`, `QUESTION_OPENED`, `QUESTION_CLOSED`, `LEADERBOARD_UPDATED`, `QUIZ_FINISHED` use `QuizState`; private `PARTICIPANT_SELECTED` and `PRIZE_WON` use `PrivateNotification`. These minimal DTOs will need explicit versioned evolution with their owning modules. Never put correct answers in an event before question closure.

The concrete business integration in F03 is `PARTICIPANT_REGISTERED`; F04 draw events are specified below. A new participant generates one admin event; a repeated registration returning an existing participant does not. Participant names are not sent to shared topics.

## Publishing from backend business services

Inject `RealtimeEvents`. Call it **inside** the business transaction. It refuses calls without an active transaction. The application event is consumed with `AFTER_COMMIT`; rollbacks emit no WebSocket messages, and delivery exceptions are caught without undoing the already committed transaction.

```java
@Transactional
public void updateEvent() {
    // Persist authoritative state first.
    realtimeEvents.event(new RealtimePayload.EventState("OPEN"));
}

@Transactional
public void notifySelected(UUID participantId) {
    // Only call after persisting the future draw state.
    realtimeEvents.privateParticipant(participantId, EventType.PARTICIPANT_SELECTED,
        new RealtimePayload.PrivateNotification("You have been selected."));
}
```

Other typed methods are `draw`, `wheel`, `quiz` and `participantRegistered`; each validates the event type for its destination. Private delivery validates that the participant exists. These APIs do not let clients select broker destinations. Do not expose private data through public payloads.

## Reconnection and deployment

Clients should monitor STOMP heartbeats, reconnect with bounded exponential backoff, obtain a fresh CSRF token, restore subscriptions and retrieve authoritative state through REST. Deduplicate with `eventId` where useful. No missed messages are replayed; use REST polling if WebSocket is unavailable. Future business modules will supply their own state endpoints. Presence is not participant count.

`WS_HEARTBEAT_MS` defaults to 10000 in each direction. `WS_MESSAGE_SIZE_BYTES` defaults to 65536, `WS_SEND_BUFFER_BYTES` to 262144, and `WS_SEND_TIMEOUT_MS` to 10000. Inbound and outbound executors have bounded queues. The HTTP session is rechecked before inbound/outbound client messages, and stale sockets are closed by a 30-second periodic sweep; database errors fail closed. Session checks have database cost and must be load tested. Spring Session also installs lifecycle integration; JDBC expiration event support differs from brokers with explicit session events, so the periodic check is retained.

Nginx must forward `/ws` as an HTTP/1.1 WebSocket upgrade with `Upgrade` and `Connection` headers and sufficient idle/read timeouts; the repository's existing proxy should be reviewed during the frontend integration phase. Terminate TLS at a trusted proxy and expose only HTTPS/WSS. An AWS ALB must use a listener and target group that support WebSocket upgrade and an idle timeout greater than the negotiated heartbeat interval; validate the complete path in deployment.

The simple broker is **instance-local**. Spring Session JDBC shares HTTP sessions, not broker messages. Run **one backend ECS task** until a distributed broker/routing design is implemented. Multiple instances can silently miss broadcasts and private messages.

## Manual test

With the backend and database running, obtain an authenticated session cookie and a fresh CSRF token using the curl examples in the README. Use a STOMP-capable client that supports sending the `SESSION` cookie in the WebSocket handshake, an allowed `Origin`, and the CSRF token in the STOMP CONNECT native header. Subscribe to `/topic/admin` as an admin; register a participant from a separate cookie jar. The admin should receive `PARTICIPANT_REGISTERED`, while a participant subscription to `/topic/admin` and direct SEND to `/topic/event` should be rejected. Log out and verify subsequent delivery stops and the socket closes. Do not use a public third-party WebSocket test site with real session cookies.

## F04 draw events

F04 adds `DRAW_CANCELLED`, `DRAW_RESET` and `DRAW_STATE_UPDATED` to the existing draw event types. On start, `/topic/draw` receives `DRAW_STARTED`, `DRAW_PARTICIPANT_SELECTED` and `DRAW_STATE_UPDATED` with `DrawUpdate {drawId, cycleNumber, status, participantNumber}`; `/topic/admin` receives `DRAW_PARTICIPANT_SELECTED` with `DrawAdmin {drawId, cycleNumber, status, participantId, participantNumber, firstName, lastName}`. The selected participant alone receives `PARTICIPANT_SELECTED` with `DrawPrivate {drawId, cycleNumber, participantNumber, message}` on their private queue. Names and UUIDs of participants are not sent to the shared draw topic.

Cancellation and internal completion publish `DRAW_CANCELLED` or `DRAW_COMPLETED` plus `DRAW_STATE_UPDATED` to the shared topic and detailed events to admins. Reset publishes `DRAW_RESET` with `DrawReset {previousCycleNumber, cycleNumber}` plus a cleared `DRAW_STATE_UPDATED`. These notifications are scheduled only within the transaction and delivered after commit. REST `/api/draw/current` and `/api/participants/me/draw` let clients recover state after reconnecting. F03's original `DrawState` publisher remains available for backward compatibility; new F04 events use the richer DTOs above.

## F05 wheel events

After successful allocation, `/topic/wheel` receives `WHEEL_STARTED`, `WHEEL_RESULT`, and `WHEEL_STATE_UPDATED` with `WheelResult {awardId,drawId,participantNumber,prizeId,prizeName,category,segments}`. Segments contain only public prize UUID/name/category, in the snapshotted display order. A separate `WHEEL_STATE_UPDATED` with `WheelAvailability {eligiblePrizes}` follows inventory or configuration changes. `/topic/admin` receives `PRIZE_AWARDED` or `PRIZE_INVENTORY_UPDATED` with `WheelAdmin {awardId,drawId,participantId,prizeId,prizeName,remainingStock}`. The winner alone receives `PRIZE_WON` with `PrizeWon {awardId,drawId,prizeId,prizeName,message}`. F04 also emits `DRAW_COMPLETED` to its existing destinations.

`WHEEL_STARTED` is a presentation signal only after the allocation commits. Events can be lost or reordered; the persisted result and REST are authoritative. An idempotent retry emits no duplicate award events. Draw reset does not change inventory, award history, or progression. See [PRIZE_WHEEL.md](PRIZE_WHEEL.md).

## Quiz handoff

`/topic/quiz` and the five existing `QUIZ_OPENED`, `QUESTION_OPENED`, `QUESTION_CLOSED`, `LEADERBOARD_UPDATED`, `QUIZ_FINISHED` event types are available, but the only currently implemented Quiz payload is `RealtimePayload.QuizState(String status)`. Question and leaderboard DTOs and business publication do not yet exist. Mohammed should coordinate additions and versioned contracts with the Tech Lead; do not publish correct answers before closure or another participant's private answer. `frontend/src/services/realtime.ts` provides a shared authenticated STOMP connection; clients should use `onConnected` to retrieve authoritative state over REST after reconnect. See [QUIZ_INTEGRATION.md](QUIZ_INTEGRATION.md).
