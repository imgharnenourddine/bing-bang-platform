package ma.enset.bigbang.auth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
class AuthenticationDetailsTest {
    @Test void bcryptAndCredentialsErasure() {
        var encoder = new BCryptPasswordEncoder();
        var hash = encoder.encode("a-long-test-password");
        assertNotEquals("a-long-test-password", hash);
        assertTrue(encoder.matches("a-long-test-password", hash));
        assertFalse(encoder.matches("incorrect", hash));
        var principal = new SessionPrincipal(UUID.randomUUID(), "admin", Role.ADMIN, true, hash);
        principal.eraseCredentials();
        assertNull(principal.getPassword());
    }
}
