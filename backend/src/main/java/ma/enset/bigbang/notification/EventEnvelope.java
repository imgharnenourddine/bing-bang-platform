package ma.enset.bigbang.notification;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T extends RealtimePayload>(UUID eventId, EventType type, Instant timestamp, int version, T payload) {
    public static <T extends RealtimePayload> EventEnvelope<T> create(EventType type, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), type, Instant.now(), 1, payload);
    }
}
