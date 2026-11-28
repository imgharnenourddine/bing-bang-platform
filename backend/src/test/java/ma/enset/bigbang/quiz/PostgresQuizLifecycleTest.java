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
@Import({QuizAdminService.class,QuizLifecycleService.class,PostgresQuizLifecycleTest.TimeConfig.class})
@EnabledIfEnvironmentVariable(named="F06_TEST_DB_URL",matches=".+")
class PostgresQuizLifecycleTest {
 private static final String SCHEMA="f063_"+UUID.randomUUID().toString().replace("-","");
 static class AdjustableClock extends Clock {
  volatile Instant time=Instant.parse("2026-11-28T13:00:00Z");
  @Override public java.time.ZoneId getZone(){return ZoneOffset.UTC;}
  @Override public Clock withZone(java.time.ZoneId zone){return this;}
  @Override public Instant instant(){return time;}
 }
 @TestConfiguration static class TimeConfig {@Bean AdjustableClock clock(){return new AdjustableClock();}}
 @DynamicPropertySource static void db(DynamicPropertyRegistry r) throws Exception {String url=System.getenv("F06_TEST_DB_URL"),user=System.getenv("F06_TEST_DB_USER"),pass=System.getenv("F06_TEST_DB_PASSWORD");try(var c=DriverManager.getConnection(url,user,pass);var s=c.createStatement()){s.execute("CREATE SCHEMA "+SCHEMA);s.execute("SET search_path TO "+SCHEMA);for(String f:new String[]{"V2__administrator_accounts.sql","V4__participants.sql","V7__quiz_foundation.sql","V8__quiz_question_timing.sql"})s.execute(Files.readString(Path.of("../database/migrations/"+f)));}r.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+SCHEMA);r.add("spring.datasource.username",()->user);r.add("spring.datasource.password",()->pass);}
 @AfterAll static void cleanup() throws Exception {try(var c=DriverManager.getConnection(System.getenv("F06_TEST_DB_URL"),System.getenv("F06_TEST_DB_USER"),System.getenv("F06_TEST_DB_PASSWORD"));var s=c.createStatement()){s.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");}}
 @Autowired QuizAdminService admin;@Autowired QuizLifecycleService lifecycle;@Autowired AdjustableClock clock;@Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @Test @Transactional(propagation=Propagation.NOT_SUPPORTED) void concurrentStartAndCloseAreSerialized() throws Exception {
  var quiz=admin.create(new QuizAdminDtos.CreateQuiz("Event"));var q=admin.createQuestion(new QuizAdminDtos.QuestionInput("Question",30,100,List.of(new QuizAdminDtos.OptionInput("Yes",true),new QuizAdminDtos.OptionInput("No",false)),quiz.version()));long version=q.quizVersion();var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){var tasks=java.util.stream.IntStream.range(0,2).mapToObj(i->pool.submit(()->{gate.await();try{return lifecycle.start(version);}catch(RuntimeException e){return null;}})).toList();gate.countDown();assertTrue((tasks.get(0).get()==null)!=(tasks.get(1).get()==null));}
  var opened=lifecycle.openFirst(lifecycle.status().lifecycleVersion());assertEquals(q.id(),opened.currentQuestionId());
  clock.time=opened.questionDeadline();var gate2=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var manual=pool.submit(()->{gate2.await();try{return lifecycle.closeCurrent(q.id(),opened.lifecycleVersion());}catch(RuntimeException e){return null;}});var automatic=pool.submit(()->{gate2.await();lifecycle.closeExpired(opened.quizId(),q.id());return true;});gate2.countDown();manual.get();assertTrue(automatic.get());}
  assertEquals(QuizStatus.QUESTION_CLOSED,lifecycle.status().status());assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM quiz_question_timings WHERE question_id=? AND closed_at IS NOT NULL",Long.class,q.id()));assertEquals(QuizStatus.FINISHED,lifecycle.finish(q.id(),lifecycle.status().lifecycleVersion()).status());
 }
}
