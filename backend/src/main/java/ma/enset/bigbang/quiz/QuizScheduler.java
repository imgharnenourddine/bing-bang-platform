package ma.enset.bigbang.quiz;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QuizScheduler {
 private static final Logger LOG=LoggerFactory.getLogger(QuizScheduler.class);
 private final JdbcTemplate jdbc;private final QuizLifecycleService service;private final Clock clock;
 public QuizScheduler(JdbcTemplate jdbc,QuizLifecycleService service,Clock clock){this.jdbc=jdbc;this.service=service;this.clock=clock;}
 private record Candidate(UUID sessionId,UUID questionId) { }
 @Scheduled(fixedDelayString="${app.quiz.expiry-poll-ms:1000}", initialDelayString="${app.quiz.expiry-initial-delay-ms:0}")
 public void closeExpired(){
  try {var candidates=jdbc.query("SELECT id,current_question_id FROM quiz_sessions WHERE status='QUESTION_ACTIVE' AND question_deadline <= ?",(rs,i)->new Candidate(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class)),java.sql.Timestamp.from(clock.instant()));
   for(var candidate:candidates){try{service.closeExpired(candidate.sessionId(),candidate.questionId());}catch(RuntimeException ex){LOG.warn("Quiz expiry reconciliation failed; it will be retried.");}}
  } catch(RuntimeException ex){LOG.warn("Quiz expiry reconciliation unavailable; it will be retried.");}
 }
}
