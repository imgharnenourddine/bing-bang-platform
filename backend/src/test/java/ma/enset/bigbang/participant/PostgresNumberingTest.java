package ma.enset.bigbang.participant;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Set F02_TEST_DB_URL, F02_TEST_DB_USER and F02_TEST_DB_PASSWORD to a disposable PostgreSQL database. */
@EnabledIfEnvironmentVariable(named = "F02_TEST_DB_URL", matches = ".+")
class PostgresNumberingTest {
    @Test void sequenceAllocatesDistinctNumbersConcurrently() throws Exception {
        String url = System.getenv("F02_TEST_DB_URL"), user = System.getenv("F02_TEST_DB_USER"), password = System.getenv("F02_TEST_DB_PASSWORD");
        String schema = "f02_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                statement.execute("CREATE SEQUENCE " + schema + ".participant_number_seq START WITH 1");
                try (var executor = Executors.newFixedThreadPool(12)) {
                    var tasks = java.util.stream.IntStream.range(0, 100).<java.util.concurrent.Callable<Integer>>mapToObj(i -> () -> {
                        try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement();
                             var rs = s.executeQuery("SELECT nextval('" + schema + ".participant_number_seq')")) {
                            rs.next(); return rs.getInt(1);
                        }
                    }).toList();
                    var values = new HashSet<Integer>();
                    for (var future : executor.invokeAll(tasks)) values.add(future.get());
                    assertEquals(100, values.size());
                    assertTrue(values.contains(1));
                    assertTrue(values.contains(100));
                }
            } finally { statement.execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
    }
}
