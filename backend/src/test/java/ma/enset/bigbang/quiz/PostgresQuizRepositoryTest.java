package ma.enset.bigbang.quiz;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import ma.enset.bigbang.participant.Participant;
import ma.enset.bigbang.participant.ParticipantRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "F06_TEST_DB_URL", matches = ".+")
class PostgresQuizRepositoryTest {
    private static final String SCHEMA = "f06_jpa_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("F06_TEST_DB_URL"), user = System.getenv("F06_TEST_DB_USER"), password = System.getenv("F06_TEST_DB_PASSWORD");
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            statement.execute("SET search_path TO " + SCHEMA);
            for (String file : new String[]{"V4__participants.sql", "V7__quiz_foundation.sql"}) statement.execute(Files.readString(Path.of("../database/migrations/" + file)));
        }
        registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> user); registry.add("spring.datasource.password", () -> password);
    }
    @AfterAll static void cleanup() throws Exception {
        try (var c = DriverManager.getConnection(System.getenv("F06_TEST_DB_URL"), System.getenv("F06_TEST_DB_USER"), System.getenv("F06_TEST_DB_PASSWORD")); var s = c.createStatement()) { s.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE"); }
    }
    @Autowired QuizSessionRepository sessions;
    @Autowired QuizQuestionRepository questions;
    @Autowired QuizOptionRepository options;
    @Autowired QuizAnswerRepository answers;
    @Autowired ParticipantRepository participants;
    @Test void repositoriesOrderAndAggregatePersistedAnswers() {
        var now = Instant.now();
        var quiz = sessions.saveAndFlush(new QuizSession(UUID.randomUUID(), "Quiz", now));
        var second = questions.saveAndFlush(new QuizQuestion(UUID.randomUUID(), quiz, "Second", 2, 30, 100, now));
        var first = questions.saveAndFlush(new QuizQuestion(UUID.randomUUID(), quiz, "First", 1, 30, 100, now));
        var option = options.saveAndFlush(new QuizOption(UUID.randomUUID(), first, "Yes", 1, true));
        var participant = participants.saveAndFlush(new Participant(UUID.randomUUID(), 777, "A", "B", now));
        answers.saveAndFlush(new QuizAnswer(UUID.randomUUID(), first, participant, option, 100));
        assertEquals(java.util.List.of(first.getId(), second.getId()), questions.findByQuizSessionIdOrderByPositionAsc(quiz.getId()).stream().map(QuizQuestion::getId).toList());
        assertEquals(second.getId(), questions.findFirstByQuizSessionIdAndPositionGreaterThanOrderByPositionAsc(quiz.getId(), 1).orElseThrow().getId());
        assertTrue(options.existsByIdAndQuestionId(option.getId(), first.getId()));
        assertTrue(answers.existsByQuestionIdAndParticipantId(first.getId(), participant.getId()));
        assertEquals(100L, answers.sumPointsByParticipant(quiz.getId()).getFirst().getPoints());
    }
}
