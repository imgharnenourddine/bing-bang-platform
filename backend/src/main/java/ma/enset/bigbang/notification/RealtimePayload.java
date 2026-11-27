package ma.enset.bigbang.notification;

import java.util.UUID;

/** Explicit DTOs: never put persistence entities or premature quiz answers in events. */
public sealed interface RealtimePayload {
    record EventState(String state) implements RealtimePayload { }
    record ParticipantRegistered(UUID id, int participantNumber, String firstName, String lastName) implements RealtimePayload { }
    record DrawState(UUID drawId, Integer participantNumber) implements RealtimePayload { }
    record WheelState(UUID drawId, String status) implements RealtimePayload { }
    record QuizState(String status) implements RealtimePayload { }
    record PrivateNotification(String message) implements RealtimePayload { }
}
