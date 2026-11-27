package ma.enset.bigbang.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private static final int MAX_KEYS = 10000;
    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Entry> attempts = new HashMap<>();
    private record Entry(int count, Instant expires) { }
    @org.springframework.beans.factory.annotation.Autowired
    public LoginRateLimiter(@Value("${app.login.max-attempts:5}") int maxAttempts,
                            @Value("${app.login.window:15m}") Duration window) {
        this(maxAttempts, window, Clock.systemUTC());
    }
    LoginRateLimiter(int maxAttempts, Duration window, Clock clock) {
        if (maxAttempts < 1 || window.isNegative() || window.isZero()) throw new IllegalArgumentException("Invalid login rate limit");
        this.maxAttempts = maxAttempts; this.window = window; this.clock = clock;
    }
    // Reserve a slot before checking credentials: concurrent requests cannot bypass the limit.
    public synchronized boolean tryAcquire(String key) {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(e -> !e.getValue().expires().isAfter(now));
        Entry current = attempts.get(key);
        if (current == null) {
            if (attempts.size() >= MAX_KEYS) return false;
            attempts.put(key, new Entry(1, now.plus(window)));
            return true;
        }
        if (current.count() >= maxAttempts) return false;
        attempts.put(key, new Entry(current.count() + 1, current.expires()));
        return true;
    }
    public synchronized void success(String key) { attempts.remove(key); }
}
