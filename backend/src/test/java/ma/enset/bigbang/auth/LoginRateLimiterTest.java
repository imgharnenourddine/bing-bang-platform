package ma.enset.bigbang.auth;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LoginRateLimiterTest {
    @Test void limitsAttemptsAndClearsOnSuccess() {
        var limiter = new LoginRateLimiter(2, Duration.ofMinutes(5), Clock.systemUTC());
        assertTrue(limiter.tryAcquire("127.0.0.1"));
        assertTrue(limiter.tryAcquire("127.0.0.1"));
        assertFalse(limiter.tryAcquire("127.0.0.1"));
        assertTrue(limiter.tryAcquire("127.0.0.2"));
        limiter.success("127.0.0.1");
        assertTrue(limiter.tryAcquire("127.0.0.1"));
    }
    @Test void rejectsUnsafeConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new LoginRateLimiter(0, Duration.ofSeconds(1), Clock.systemUTC()));
    }
}
