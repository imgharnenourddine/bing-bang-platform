package ma.enset.bigbang.quiz;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=validate","spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({QuizAdminService.class,QuizLifecycleService.class,QuizAnswerService.class,QuizResultsService.class,PostgresQuizResultsTest.TimeConfig.class})
@EnabledIfEnvironmentVariable(named="F06_TEST_DB_URL",matches=".+")
class PostgresQuizResultsTest {
 private static final String SCHEMA="f065_"+UUID.randomUUID().toString().replace("-","");
 static class AdjustableClock extends Clock {
  volatile Instant time=Instant.parse("2026-11-28T13:00:00Z");
  @Override public java.time.ZoneId getZone(){return ZoneOffset.UTC;}
  @Override public Clock withZone(java.time.ZoneId zone){return this;}
  @Override public Instant instant(){return time;}
 }
 @TestConfiguration static class TimeConfig {@Bean AdjustableClock clock(){return new AdjustableClock();}}
 @DynamicPropertySource static void db(DynamicPropertyRegistry r) throws Exception {String url=System.getenv("F06_TEST_DB_URL"),user=System.getenv("F06_TEST_DB_USER"),pass=System.getenv("F06_TEST_DB_PASSWORD");try(var c=DriverManager.getConnection(url,user,pass);var s=c.createStatement()){s.execute("CREATE SCHEMA "+SCHEMA);s.execute("SET search_path TO "+SCHEMA);for(String f:new String[]{"V2__administrator_accounts.sql","V4__participants.sql","V7__quiz_foundation.sql","V8__quiz_question_timing.sql"})s.execute(Files.readString(Path.of("../database/migrations/"+f)));}r.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+SCHEMA);r.add("spring.datasource.username",()->user);r.add("spring.datasource.password",()->pass);}
 @AfterAll static void cleanup() throws Exception {try(var c=DriverManager.getConnection(System.getenv("F06_TEST_DB_URL"),System.getenv("F06_TEST_DB_USER"),System.getenv("F06_TEST_DB_PASSWORD"));var s=c.createStatement()){s.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");}}
 @Autowired QuizResultsService results;@Autowired QuizAdminService admin;@Autowired QuizLifecycleService lifecycle;@Autowired QuizAnswerService answerService;@Autowired AdjustableClock clock;@Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @Test @Transactional(propagation=Propagation.NOT_SUPPORTED) void concurrentSubmissionsAndRecoveryAreAtomic() throws Exception {
  var quiz=admin.create(new QuizAdminDtos.CreateQuiz("Event"));var q=admin.createQuestion(new QuizAdminDtos.QuestionInput("Question",30,100,List.of(new QuizAdminDtos.OptionInput("Yes",true),new QuizAdminDtos.OptionInput("No",false)),quiz.version()));
  lifecycle.start(q.quizVersion());var opened=lifecycle.openFirst(lifecycle.status().lifecycleVersion());
  UUID person=UUID.randomUUID(), other=UUID.randomUUID();jdbc.update("INSERT INTO participants(id,first_name,last_name,created_at) VALUES (?,'A','B',?), (?,'C','D',?)",person,java.sql.Timestamp.from(clock.time.minusSeconds(1)),other,java.sql.Timestamp.from(clock.time.plusSeconds(1)));
  clock.time=clock.time.plusSeconds(2);
  for(int i=0;i<100;i++) jdbc.update("INSERT INTO participants(id,first_name,last_name,created_at) VALUES (?,'Unanswered','Participant',?)",UUID.randomUUID(),java.sql.Timestamp.from(clock.time.minusSeconds(3)));
  var correct=q.options().stream().filter(QuizAdminDtos.Option::correct).findFirst().orElseThrow();var wrong=q.options().stream().filter(o->!o.correct()).findFirst().orElseThrow();
  var gate=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var tasks=java.util.stream.IntStream.range(0,2).mapToObj(i->pool.submit(()->{gate.await();return answerService.submit(person,new QuizAnswerDtos.Submit(q.id(),correct.id()));})).toList();gate.countDown();assertTrue(tasks.get(0).get().created()!=tasks.get(1).get().created());}
  assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM quiz_answers WHERE question_id=? AND participant_id=?",Long.class,q.id(),person));
  assertEquals(100,jdbc.queryForObject("SELECT awarded_points FROM quiz_answers WHERE question_id=? AND participant_id=?",Integer.class,q.id(),person));
  assertTrue(answerService.submit(other,new QuizAnswerDtos.Submit(q.id(),wrong.id())).created());assertEquals(0,jdbc.queryForObject("SELECT awarded_points FROM quiz_answers WHERE question_id=? AND participant_id=?",Integer.class,q.id(),other));
  assertThrows(QuizAdminException.class,()->answerService.submit(person,new QuizAnswerDtos.Submit(q.id(),wrong.id())));
  clock.time=opened.questionDeadline();assertFalse(answerService.submit(person,new QuizAnswerDtos.Submit(q.id(),correct.id())).created());assertThrows(QuizAdminException.class,()->answerService.feedback(person,q.id()));
  lifecycle.closeExpired(opened.quizId(),q.id());var feedback=answerService.feedback(person,q.id());assertTrue(feedback.correct());assertEquals(100,feedback.awardedPoints());assertEquals(2,answerService.statistics(q.id()).submittedAnswers());
  lifecycle.finish(q.id(), lifecycle.status().lifecycleVersion());
  var board=results.leaderboard();assertEquals(102,board.totalParticipants());assertEquals(100,board.maximumScore());assertEquals(100,board.leaderboard().getFirst().totalScore());
  assertEquals(100,results.personal(person).totalScore());assertEquals(0,results.personal(other).correctAnswers());
  UUID late=UUID.randomUUID();jdbc.update("INSERT INTO participants(id,first_name,last_name,created_at) VALUES (?,'Late','Person',?)",late,java.sql.Timestamp.from(clock.time.plusSeconds(1)));
  assertEquals(102,results.leaderboard().totalParticipants());assertThrows(QuizAdminException.class,()->results.personal(late));
 }
}
