package ma.enset.bigbang.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class AdminBootstrap {
    @Bean
    ApplicationRunner bootstrapAdmin(JdbcTemplate jdbc, TransactionTemplate transactions, PasswordEncoder encoder,
            @Value("${app.bootstrap.username:}") String username, @Value("${app.bootstrap.password:}") String password) {
        return args -> transactions.executeWithoutResult(status -> {
            // Serialize first-run checks across all instances until the transaction commits.
            jdbc.execute("SELECT pg_advisory_xact_lock(481526991)");
            if (username.isBlank() != password.isBlank()) throw new IllegalStateException("Set both ADMIN_USERNAME and ADMIN_PASSWORD, or neither");
            Long count = jdbc.queryForObject("SELECT count(*) FROM administrator_accounts", Long.class);
            if (count != null && count > 0) return;
            if (username.isBlank() || username.length() > 100 || !username.equals(username.trim()) ||
                    password.getBytes(StandardCharsets.UTF_8).length < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72 || password.isBlank()) {
                throw new IllegalStateException("First startup requires ADMIN_USERNAME and ADMIN_PASSWORD (12-72 UTF-8 bytes)");
            }
            jdbc.update("INSERT INTO administrator_accounts (id, username, password_hash, enabled) VALUES (?, ?, ?, true)",
                    UUID.randomUUID(), username, encoder.encode(password));
        });
    }
}
