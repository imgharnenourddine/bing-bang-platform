# Quiz results and administrator leaderboard

Results are available only after the quiz reaches `FINISHED`; earlier requests receive HTTP 409 `QUIZ_RESULTS_NOT_AVAILABLE`. No results are cached in memory. The existing question feedback endpoint continues to provide each participant's own correction after closure.

The MVP population contains participants registered at or before the quiz opening timestamp, including those with no answer, and also later registrants with at least one accepted answer in this quiz. F06.4 accepts late answers, so excluding their authors would contradict that contract. Later registrations without an accepted answer receive 404 `QUIZ_PARTICIPANT_NOT_ELIGIBLE` and do not change the completed leaderboard. Registration timestamps and quiz answers must remain immutable for stable results.

## API and confidentiality

- `GET /api/admin/quiz/leaderboard` requires `ROLE_ADMIN` and returns quiz identity, finished time, question count, maximum score, eligible count, answered count, highest score, mean score, number tied for first, and entries with participant public number, name, score, answer counts and rank. Example entry: `{"rank":1,"participantNumber":42,"firstName":"Example","lastName":"Name","totalScore":100,"answeredQuestions":1,"correctAnswers":1,"incorrectAnswers":0,"unansweredQuestions":0}`.
- `GET /api/quiz/me/results` requires `ROLE_PARTICIPANT`. The participant UUID comes only from the authenticated session. Example: `{"quizId":"<uuid>","title":"Quiz","status":"FINISHED","totalQuestions":2,"answeredQuestions":1,"correctAnswers":1,"incorrectAnswers":0,"unansweredQuestions":1,"totalScore":100,"maximumScore":200,"accuracyPercentage":50.0,"finishedAt":"<instant>"}`. No personal rank, other participant, winner, distribution or leaderboard is returned. Ineligible participants receive 404.

Scores sum stored `awarded_points`, never recalculate historical awards. Maximum score is the sum of question points; counts use accepted answers and the stored option correctness. Unanswered contributes zero. Accuracy is correct answers divided by **all questions**, including unanswered questions, multiplied by 100 and rounded half up to one decimal; a zero-question quiz returns 0.0 defensively. Aggregate sums use 64-bit values.

The administrator list sorts score descending and public number ascending for deterministic presentation. Competition ranks depend on score only: `1, 2, 2, 4`. All participants with rank 1 share first place; no prize is allocated automatically. Summary average is rounded half up to one decimal. Empty populations have zero highest score and no winners.

PostgreSQL computes participant aggregates in one grouped query, with a bounded separate question totals query. Personal requests use the same aggregation scoped to the authenticated UUID and do not build the leaderboard. The read-only repeatable-read transaction gives each response a consistent snapshot. No migration or new index is required by this implementation; performance and the SQL need verification on PostgreSQL.

Future F06.6 notifications must not disclose ranks, scores, winner identities or answer distributions to participant destinations. Participants use their personal REST endpoint; administrators use their protected leaderboard endpoint. No Quiz results events are published by this milestone.

Frontend roadmap: A07 administrator quiz management; A08 administrator leaderboard; P05 participant quiz; P06 participant personal results. These interfaces are pending.

## Verification

Run `cd backend && ./mvnw clean verify`. `PostgresQuizResultsTest` requires `F06_TEST_DB_URL`, `F06_TEST_DB_USER`, and `F06_TEST_DB_PASSWORD` for a disposable PostgreSQL database where the user can create and drop its test schema. It exercises the persisted score and late-registration policy with over 100 participants. Run `git diff --check` and `docker compose config --quiet` at the repository root. Live authenticated HTTP and concurrent/database checks require a running backend and PostgreSQL; their success must not be inferred from unit tests.
