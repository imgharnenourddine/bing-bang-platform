package ma.enset.bigbang.draw;

import java.time.Instant;
import java.util.UUID;

/** Immutable database projection, not exposed directly over HTTP. */
public record DrawRecord(UUID id, long cycleId, int cycleNumber, UUID participantId, int participantNumber,
                         String firstName, String lastName, UUID selectedBy, DrawStatus status,
                         Instant createdAt, Instant completedAt, Instant cancelledAt, UUID cancelledBy) { }
