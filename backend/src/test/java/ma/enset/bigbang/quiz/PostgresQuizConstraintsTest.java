package ma.enset.bigbang.quiz;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Uses a fresh schema in a disposable PostgreSQL database supplied explicitly by the operator. */
@EnabledIfEnvironmentVariable(named = "F06_TEST_DB_URL", matches = ".+")
class PostgresQuizConstraintsTest {
    @Test void constraintsAndOrderingAreEnforced() throws Exception {
        String url = System.getenv("F06_TEST_DB_URL"), user = System.getenv("F06_TEST_DB_USER"), pass = System.getenv("F06_TEST_DB_PASSWORD");
        String schema = "f06_" + UUID.randomUUID().toString().replace("-", "");
        try (var root = DriverManager.getConnection(url, user, pass); var stmt = root.createStatement()) {
            stmt.execute("CREATE SCHEMA " + schema);
            try {
                String scoped = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
                var jdbc = new JdbcTemplate(new DriverManagerDataSource(scoped, user, pass));
                for (String file : new String[]{"V4__participants.sql", "V7__quiz_foundation.sql"}) jdbc.execute(Files.readString(Path.of("../database/migrations/" + file)));
                UUID quiz = UUID.randomUUID(), otherQuiz = UUID.randomUUID(), q1 = UUID.randomUUID(), q2 = UUID.randomUUID(), option = UUID.randomUUID(), person = UUID.randomUUID();
                jdbc.update("INSERT INTO quiz_sessions(id,title,status,created_at,updated_at) VALUES (?,?, 'DRAFT',now(),now()), (?,?,'DRAFT',now(),now())", quiz, "Quiz", otherQuiz, "Other");
                jdbc.update("INSERT INTO quiz_questions(id,quiz_session_id,question_text,position,duration_seconds,points,created_at,updated_at) VALUES (?,?, 'First',1,30,100,now(),now()), (?,?, 'Second',2,30,100,now(),now())", q1, quiz, q2, quiz);
                assertThrows(Exception.class, () -> jdbc.update("INSERT INTO quiz_questions(id,quiz_session_id,question_text,position,duration_seconds,points,created_at,updated_at) VALUES (?,?,'Duplicate',1,30,100,now(),now())", UUID.randomUUID(), quiz));
                assertThrows(Exception.class, () -> jdbc.update("INSERT INTO quiz_questions(id,quiz_session_id,question_text,position,duration_seconds,points,created_at,updated_at) VALUES (?,?,'Bad',3,0,100,now(),now())", UUID.randomUUID(), quiz));
                assertThrows(Exception.class, () -> jdbc.update("INSERT INTO quiz_questions(id,quiz_session_id,question_text,position,duration_seconds,points,created_at,updated_at) VALUES (?,?,'Bad',3,30,0,now(),now())", UUID.randomUUID(), quiz));
                assertEquals(java.util.List.of(q1,q2), jdbc.query("SELECT id FROM quiz_questions WHERE quiz_session_id=? ORDER BY position", (rs,i)->rs.getObject(1,UUID.class), quiz));
                jdbc.update("INSERT INTO quiz_options(id,question_id,option_text,position,correct) VALUES (?,?,'Yes',1,true)", option,q1);
                assertThrows(Exception.class, () -> jdbc.update("INSERT INTO quiz_options(id,question_id,option_text,position,correct) VALUES (?,?,'No',1,false)", UUID.randomUUID(),q1));
                assertThrows(Exception.class, () -> jdbc.update("INSERT INTO quiz_options(id,question_id,option_text,position,correct) VALUES (?,?,'No',0,false)", UUID.randomUUID(),q1));
                assertThrows(Exception.class, () -> jdbc.update("INSERT INTO quiz_options(id,question_id,option_text,position,correct) VALUES (?,?,'Other',2,true)", UUID.randomUUID(),q1));
                jdbc.update("INSERT INTO participants(id,first_name,last_name,created_at) VALUES (?,'A','B',now())",person);
                jdbc.update("INSERT INTO quiz_answers(id,question_id,participant_id,selected_option_id,awarded_points) VALUES (?,?,?,?,100)", UUID.randomUUID(),q1,person,option);
                assertThrows(Exception.class, () -> jdbc.update("INSERT INTO quiz_answers(id,question_id,participant_id,selected_option_id,awarded_points) VALUES (?,?,?,?,0)", UUID.randomUUID(),q1,person,option));
                assertThrows(Exception.class, () -> jdbc.update("INSERT INTO quiz_answers(id,question_id,participant_id,selected_option_id,awarded_points) VALUES (?,?,?,?,0)", UUID.randomUUID(),q2,person,option));
                assertThrows(Exception.class, () -> jdbc.update("INSERT INTO quiz_answers(id,question_id,participant_id,selected_option_id,awarded_points) VALUES (?,?,?,?,0)", UUID.randomUUID(),q1,UUID.randomUUID(),option));
                UUID anotherPerson = UUID.randomUUID();
                jdbc.update("INSERT INTO participants(id,first_name,last_name,created_at) VALUES (?,'C','D',now())",anotherPerson);
                assertThrows(Exception.class, () -> jdbc.update("INSERT INTO quiz_answers(id,question_id,participant_id,selected_option_id,awarded_points) VALUES (?,?,?,?,-1)", UUID.randomUUID(),q1,anotherPerson,option));
                assertThrows(Exception.class, () -> jdbc.update("UPDATE quiz_sessions SET status='QUESTION_ACTIVE',opened_at=now(),current_question_id=?,question_opened_at=now(),question_deadline=now()+interval '30 seconds' WHERE id=?",q1,otherQuiz));
                jdbc.update("UPDATE quiz_sessions SET status='OPEN',opened_at=now() WHERE id=?",quiz);
                assertThrows(Exception.class, () -> jdbc.update("UPDATE quiz_sessions SET status='OPEN',opened_at=now() WHERE id=?",otherQuiz));
            } finally { stmt.execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
    }
}
