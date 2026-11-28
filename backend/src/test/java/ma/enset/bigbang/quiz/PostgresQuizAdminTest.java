package ma.enset.bigbang.quiz;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=none","spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import(QuizAdminService.class)
@EnabledIfEnvironmentVariable(named="F06_TEST_DB_URL",matches=".+")
class PostgresQuizAdminTest {
    private static final String SCHEMA="f062_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) throws Exception {
        String url=System.getenv("F06_TEST_DB_URL"),user=System.getenv("F06_TEST_DB_USER"),pass=System.getenv("F06_TEST_DB_PASSWORD");
        try(var c=DriverManager.getConnection(url,user,pass);var s=c.createStatement()) {s.execute("CREATE SCHEMA "+SCHEMA);s.execute("SET search_path TO "+SCHEMA);for(String f:new String[]{"V4__participants.sql","V7__quiz_foundation.sql"})s.execute(Files.readString(Path.of("../database/migrations/"+f)));}
        r.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+SCHEMA);r.add("spring.datasource.username",()->user);r.add("spring.datasource.password",()->pass);
    }
    @AfterAll static void cleanup() throws Exception {try(var c=DriverManager.getConnection(System.getenv("F06_TEST_DB_URL"),System.getenv("F06_TEST_DB_USER"),System.getenv("F06_TEST_DB_PASSWORD"));var s=c.createStatement()){s.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");}}
    @Autowired QuizAdminService service;
    QuizAdminDtos.QuestionInput question(String text,long v){return new QuizAdminDtos.QuestionInput(text,30,100,List.of(new QuizAdminDtos.OptionInput("Yes",true),new QuizAdminDtos.OptionInput("No",false)),v);}
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED) void atomicCreationConcurrentStaleAndReordering() throws Exception {
        var created=service.create(new QuizAdminDtos.CreateQuiz("Event"));
        var quiz=service.update(new QuizAdminDtos.UpdateQuiz("Updated event",created.version()));assertEquals("Updated event",service.get().title());assertThrows(QuizAdminException.class,()->service.create(new QuizAdminDtos.CreateQuiz("Duplicate")));
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var tasks=java.util.stream.IntStream.range(0,2).mapToObj(i->pool.submit(()->{gate.await();try{return service.createQuestion(question("Q"+i,quiz.version()));}catch(QuizAdminException e){return null;}})).toList();
            gate.countDown();assertTrue((tasks.get(0).get()==null)!=(tasks.get(1).get()==null));
        }
        var first=service.list().getFirst();var latest=service.get();var second=service.createQuestion(question("Second",latest.version()));
        var revised=service.updateQuestion(first.id(),question("Revised",service.get().version()));assertEquals("Revised",service.getQuestion(revised.id()).text());assertEquals(2,revised.options().size());
        var before=service.get();assertThrows(QuizAdminException.class,()->service.createQuestion(question("Stale",quiz.version())));
        assertThrows(QuizAdminException.class,()->service.createQuestion(new QuizAdminDtos.QuestionInput("Invalid",30,100,List.of(new QuizAdminDtos.OptionInput("A",true),new QuizAdminDtos.OptionInput("B",true)),before.version())));
        assertEquals(2,service.list().size());service.reorder(new QuizAdminDtos.Reorder(List.of(second.id(),first.id()),before.version()));
        assertEquals(List.of(second.id(),first.id()),service.list().stream().map(QuizAdminDtos.Question::id).toList());
        assertTrue(service.readiness().ready());
        service.deleteQuestion(second.id(),service.get().version());assertEquals(1,service.list().getFirst().position());
    }
}
