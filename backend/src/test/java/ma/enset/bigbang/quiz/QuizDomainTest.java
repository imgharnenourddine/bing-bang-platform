package ma.enset.bigbang.quiz;

import java.time.Instant;
import java.util.UUID;
import ma.enset.bigbang.participant.Participant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuizDomainTest {
    private final Instant now = Instant.parse("2026-11-28T13:00:00Z");
    @Test void createsDraftAndValidQuestionOptionAndAnswer() {
        var session = new QuizSession(UUID.randomUUID(), "  Event quiz  ", now);
        var question = new QuizQuestion(UUID.randomUUID(), session, "Question?", 1, 30, 100, now);
        var option = new QuizOption(UUID.randomUUID(), question, "Yes", 1, true);
        var person = new Participant(UUID.randomUUID(), 1, "A", "B", now);
        var answer = new QuizAnswer(UUID.randomUUID(), question, person, option, 100);
        assertEquals(QuizStatus.DRAFT, session.getStatus()); assertEquals("Event quiz", session.getTitle());
        assertEquals(30, question.getDurationSeconds()); assertEquals(100, answer.getAwardedPoints()); assertNotNull(answer.getSubmittedAt());
    }
    @Test void rejectsInvalidValuesAndCrossQuestionOption() {
        var session = new QuizSession(UUID.randomUUID(), "Quiz", now);
        assertThrows(IllegalArgumentException.class, () -> new QuizSession(UUID.randomUUID(), " ", now));
        assertThrows(IllegalArgumentException.class, () -> new QuizQuestion(UUID.randomUUID(), session, "Q", 1, 0, 100, now));
        assertThrows(IllegalArgumentException.class, () -> new QuizQuestion(UUID.randomUUID(), session, "Q", 1, 30, 0, now));
        var one = new QuizQuestion(UUID.randomUUID(), session, "Q1", 1, 30, 100, now);
        var two = new QuizQuestion(UUID.randomUUID(), session, "Q2", 2, 30, 100, now);
        assertThrows(IllegalArgumentException.class, () -> new QuizOption(UUID.randomUUID(), one, "x", 0, false));
        var option = new QuizOption(UUID.randomUUID(), two, "x", 1, false);
        var participant = new Participant(UUID.randomUUID(), 1, "A", "B", now);
        assertThrows(IllegalArgumentException.class, () -> new QuizAnswer(UUID.randomUUID(), one, participant, option, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuizAnswer(UUID.randomUUID(), two, participant, option, -1));
    }
}
