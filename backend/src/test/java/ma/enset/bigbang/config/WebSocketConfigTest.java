package ma.enset.bigbang.config;

import ma.enset.bigbang.notification.SessionGuard;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketConfigTest {
    @Test void refusesWildcardOriginsAndUnsafeTransportLimits() {
        var scheduler = new ThreadPoolTaskScheduler();
        var guard = mock(SessionGuard.class);
        assertThrows(IllegalArgumentException.class, () -> new WebSocketConfig<>("*", 10000, 65536, 262144, 10000, scheduler, guard));
        assertThrows(IllegalArgumentException.class, () -> new WebSocketConfig<>("https://example.com", 100, 65536, 262144, 10000, scheduler, guard));
        assertDoesNotThrow(() -> new WebSocketConfig<>("https://example.com", 10000, 65536, 262144, 10000, scheduler, guard));
    }
}
