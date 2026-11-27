package ma.enset.bigbang.participant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Uses a disposable schema; requires the F02_TEST_DB_* PostgreSQL environment variables. */
@EnabledIfEnvironmentVariable(named = "F02_TEST_DB_URL", matches = ".+")
class PostgresRegistrationConcurrencyTest {
    @Test void sameEstablishedSessionCreatesOnlyOneParticipant() throws Exception {
        String url = System.getenv("F02_TEST_DB_URL"), user = System.getenv("F02_TEST_DB_USER"), password = System.getenv("F02_TEST_DB_PASSWORD");
        String schema = "f02_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var root = DriverManager.getConnection(url, user, password); var statement = root.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
                var dataSource = new DriverManagerDataSource(schemaUrl, user, password);
                var jdbc = new JdbcTemplate(dataSource);
                jdbc.execute(Files.readString(Path.of("../database/migrations/V4__participants.sql")));
                var repository = mock(ParticipantRepository.class);
                when(repository.saveAndFlush(any(Participant.class))).thenAnswer(invocation -> {
                    Participant p = invocation.getArgument(0);
                    jdbc.update("INSERT INTO participants(id, participant_number, first_name, last_name, created_at) VALUES (?, ?, ?, ?, ?)",
                            p.getId(), p.getParticipantNumber(), p.getFirstName(), p.getLastName(), java.sql.Timestamp.from(p.getCreatedAt()));
                    return p;
                });
                when(repository.findById(any(UUID.class))).thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    var rows = jdbc.query("SELECT * FROM participants WHERE id = ?", (rs, row) ->
                            new Participant(id, rs.getInt("participant_number"), rs.getString("first_name"),
                                    rs.getString("last_name"), rs.getTimestamp("created_at").toInstant()), id);
                    return rows.stream().findFirst();
                });
                var service = new ParticipantService(repository, jdbc, mock(ma.enset.bigbang.notification.RealtimeEvents.class));
                var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
                var start = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var calls = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return transactions.execute(status -> service.register("same-session", new RegistrationRequest("Ahmed", "Alami")));
                    })).toList();
                    start.countDown();
                    var first = calls.get(0).get(); var second = calls.get(1).get();
                    assertNotNull(first); assertNotNull(second);
                    assertEquals(first.participant().id(), second.participant().id());
                    assertNotEquals(first.created(), second.created());
                    assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM participants", Long.class));
                    var different = transactions.execute(status -> service.register("other-session", new RegistrationRequest("Ahmed", "Alami")));
                    assertNotNull(different);
                    assertNotEquals(first.participant().participantNumber(), different.participant().participantNumber());
                    assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM participants", Long.class));
                }
            } finally { statement.execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
    }
}
