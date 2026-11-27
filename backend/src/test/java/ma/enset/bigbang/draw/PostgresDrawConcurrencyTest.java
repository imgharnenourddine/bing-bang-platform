package ma.enset.bigbang.draw;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import ma.enset.bigbang.notification.RealtimeEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** PostgreSQL only. Supply a disposable F04_TEST_DB_URL, F04_TEST_DB_USER and F04_TEST_DB_PASSWORD. */
@EnabledIfEnvironmentVariable(named = "F04_TEST_DB_URL", matches = ".+")
class PostgresDrawConcurrencyTest {
    @Test void serializesDrawsAndResetsAndKeepsHistory() throws Exception {
        String url = System.getenv("F04_TEST_DB_URL"), user = System.getenv("F04_TEST_DB_USER"), pass = System.getenv("F04_TEST_DB_PASSWORD");
        String schema = "f04_" + UUID.randomUUID().toString().replace("-", "");
        try (var root = DriverManager.getConnection(url, user, pass); var statement = root.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                String scoped = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
                var ds = new DriverManagerDataSource(scoped, user, pass);
                var jdbc = new JdbcTemplate(ds);
                for (String file : new String[]{"V2__administrator_accounts.sql", "V4__participants.sql", "V5__draw_engine.sql"})
                    jdbc.execute(Files.readString(Path.of("../database/migrations/" + file)));
                UUID admin = UUID.randomUUID(), person = UUID.randomUUID();
                jdbc.update("INSERT INTO administrator_accounts(id,username,password_hash,enabled) VALUES (?,?,?,true)", admin, "test", "hash");
                jdbc.update("INSERT INTO participants(id,first_name,last_name,created_at) VALUES (?,?,?,CURRENT_TIMESTAMP)", person, "A", "B");
                var service = new DrawService(new DrawRepository(jdbc), mock(RealtimeEvents.class));
                var transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));
                var gate = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var futures = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                        gate.await();
                        try { return transactions.execute(status -> service.start(admin)); }
                        catch (DrawException e) { return null; }
                    })).toList();
                    gate.countDown();
                    var a = futures.get(0).get(); var b = futures.get(1).get();
                    assertTrue((a == null) != (b == null));
                    var winner = a != null ? a : b;
                    assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM draws WHERE status='ACTIVE'", Long.class));
                    assertThrows(DrawException.class, () -> transactions.execute(status -> service.reset(admin, new DrawDtos.ResetRequest(true, 1))));
                    transactions.execute(status -> service.cancel(winner.id(), admin));
                    assertThrows(DrawException.class, () -> transactions.execute(status -> service.start(admin)));
                    var reset = transactions.execute(status -> service.reset(admin, new DrawDtos.ResetRequest(true, 1)));
                    assertEquals(2, reset.cycleNumber());
                    assertThrows(DrawException.class, () -> transactions.execute(status -> service.reset(admin, new DrawDtos.ResetRequest(true, 1))));
                    var second = transactions.execute(status -> service.start(admin));
                    assertEquals(person, second.participant().id());
                    assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM draws", Long.class));
                    assertThrows(Exception.class, () -> jdbc.update("INSERT INTO draws(id,cycle_id,participant_id,selected_by,status) SELECT ?,cycle_id,participant_id,selected_by,'ACTIVE' FROM draws WHERE id=?", UUID.randomUUID(), second.id()));
                    transactions.execute(status -> service.cancel(second.id(), admin));
                    var gate2 = new CountDownLatch(1);
                    var start = executor.submit(() -> { gate2.await(); try { return transactions.execute(status -> service.start(admin)); } catch (DrawException e) { return null; } });
                    var resetRace = executor.submit(() -> { gate2.await(); try { return transactions.execute(status -> service.reset(admin, new DrawDtos.ResetRequest(true, 2))); } catch (DrawException e) { return null; } });
                    gate2.countDown();
                    var started = start.get(); var racedReset = resetRace.get();
                    // Cycle 2 has no eligible participant; reset must win before a start can succeed.
                    if (started != null) {
                        assertNotNull(racedReset);
                        assertEquals(3, started.cycleNumber());
                        transactions.execute(status -> service.cancel(started.id(), admin));
                    }
                    int currentCycle = jdbc.queryForObject("SELECT cycle_number FROM draw_cycles WHERE closed_at IS NULL", Integer.class);
                    var gate3 = new CountDownLatch(1);
                    var resets = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                        gate3.await();
                        try { return transactions.execute(status -> service.reset(admin, new DrawDtos.ResetRequest(true, currentCycle))); }
                        catch (DrawException e) { return null; }
                    })).toList();
                    gate3.countDown();
                    assertTrue((resets.get(0).get() == null) != (resets.get(1).get() == null));
                    assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM draw_cycles WHERE closed_at IS NULL", Long.class));
                }
            } finally { statement.execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
    }
}
