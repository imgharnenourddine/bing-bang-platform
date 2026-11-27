# Weighted prize wheel (F05)

F05 implements the backend. Compilation, PostgreSQL concurrency, WebSocket and end-to-end tests must still be run in F07. Frontend animation is not implemented.

## Inventory and configuration

An administrator creates a STANDARD or PREMIUM prize with a positive decimal base weight and non-negative opening stock. Names and categories are explicit, prizes are disabled rather than deleted, and history uses snapshots. `initialStock` is the stock at creation and never changes; `remainingStock` decreases on award and changes through explicit stock adjustments. Each adjustment records its previous value, signed delta, result, reason, administrator and time in `prize_stock_adjustments`. The regular PATCH cannot change stock. Prizes are not preloaded.

The persistent singleton configuration defaults to growth `1.5`, cap `10`, consecutive standard awards `0`. Admins may change growth and cap without resetting the counter. Growth range is 1–100; cap is 1–1000; each has at most six fractional digits. Prize base weight is 0.000001–1000000000 with at most six fractional digits. Stock is between 0 and 2147483647. These bounds are checked by the API and database. The counter is a non-negative 64-bit integer; overflow fails and rolls back rather than wrapping.

## Selection

For consecutive STANDARD count `k`, premium multiplier is `min(cap, growth^k)`; the bounded exponentiation uses decimal arithmetic and rounds the multiplier to six fractional digits, HALF_EVEN. A STANDARD effective weight is its base; a PREMIUM effective weight is base times that multiplier. Only enabled prizes with positive stock are included. Stock is an eligibility check, not a weight multiplier.

Example: standard base 3, premium base 1, growth 1.5, k=2: multiplier 2.25, effective weights 3 and 2.25, giving probabilities 3/5.25 and 2.25/5.25. A STANDARD award increments k; a PREMIUM award resets it to zero. The counter and stock are independent of draw cycle reset. No fixed attempt guarantees a premium prize.

The selector converts effective weights to exact integer units at scale 12, uses `SecureRandom` rejection sampling for an unbiased threshold below their total, then selects the first cumulative interval containing it. Probabilities are recorded rounded to 30 decimal places; probability below that precision fails closed. F04 selected the participant separately. Neither seed nor outcome is client supplied.

## Transactions, locks and retry

Spin locks in this order: F04 `draw_control` singleton, wheel configuration row, then prize rows in UUID order. Inventory creation, modification, adjustment and config updates take the same control/config order before modifying prizes. Cancellation and draw reset take the same F04 control lock. The targeted draw must exist and be ACTIVE unless its award already exists. A retry checks the stored award after the control lock and returns HTTP 200, with no stock, progression or event changes, even if a newer draw is active. An award has a unique `draw_id` constraint.

Stock decrement, award and eligible-segment snapshots, progression update and F04 draw completion occur in one transaction; failures roll back together. The award stores names, category, weights, probability, participant number and the exact selection segment snapshot at award time. Foreign keys keep historical references intact. F04 completion is called under the same transaction and lock; it has no separate commit. Events are emitted after commit and may be lost; REST is authoritative. No outbox is present.

## Animation and recovery

Backend selection and persisted award determine the result. Future UI may render **equal display sectors**, explicitly indicating that selection is weighted and the sectors are not probabilities. The persisted segment snapshot provides stable prize UUID/name/category order for this representation and preserves the selection-time effective weights and probabilities for admins. If a future UI instead renders weighted sectors, it must use the stored probabilities and handle decimal rounding. Clients animate toward the persisted winner; animation failure cannot affect an award. `GET /api/wheel/status` returns the latest persisted public result and segment snapshot; a participant can recover their own history with `/api/participants/me/prizes`.

A participant's own history includes only public award information, not stock quantities, admin identity, or internal probability snapshots. Public events omit participant UUID and name. Admin snapshots include counts and weights. F03's simple broker remains single-instance and has no replay.

## Manual flow

Use the existing authenticated admin cookie jar and obtain a fresh CSRF token as described in README. Example requests (replace IDs):

```bash
TOKEN=$(curl -sS -b /tmp/bigbang-cookies -c /tmp/bigbang-cookies http://localhost:8080/api/auth/csrf | jq -r .token)
curl -sS -b /tmp/bigbang-cookies -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $TOKEN" -d '{"name":"Gift","category":"STANDARD","baseWeight":1,"initialStock":3,"enabled":true}' http://localhost:8080/api/admin/prizes
curl -sS -b /tmp/bigbang-cookies http://localhost:8080/api/admin/wheel/status
curl -sS -b /tmp/bigbang-cookies -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $TOKEN" -d '{"drawId":"DRAW_UUID"}' http://localhost:8080/api/admin/wheel/spin
curl -sS -b /tmp/bigbang-cookies http://localhost:8080/api/admin/wheel/history
```

Register participants and start an active draw before spinning. Replace `DRAW_UUID` with the actual UUID. Do not use real credentials in shell history. The inventory/audit schema, migration, idempotence, probability boundaries, concurrent spin/adjustment/cancellation and notifications require validation on PostgreSQL with real sessions; optional PostgreSQL tests use the `F05_TEST_DB_URL`, `F05_TEST_DB_USER`, `F05_TEST_DB_PASSWORD` variables and a disposable test database.
