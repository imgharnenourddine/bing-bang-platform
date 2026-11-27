package ma.enset.bigbang.notification;

import java.util.UUID;

/** Explicit DTOs: never put persistence entities or premature quiz answers in events. */
public sealed interface RealtimePayload {
    record EventState(String state) implements RealtimePayload { }
    record ParticipantRegistered(UUID id, int participantNumber, String firstName, String lastName) implements RealtimePayload { }
    record DrawState(UUID drawId, Integer participantNumber) implements RealtimePayload { }
    record DrawUpdate(UUID drawId, int cycleNumber, String status, Integer participantNumber) implements RealtimePayload { }
    record DrawAdmin(UUID drawId, int cycleNumber, String status, UUID participantId, int participantNumber, String firstName, String lastName) implements RealtimePayload { }
    record DrawPrivate(UUID drawId, int cycleNumber, int participantNumber, String message) implements RealtimePayload { }
    record DrawReset(int previousCycleNumber, int cycleNumber) implements RealtimePayload { }
    record WheelState(UUID drawId, String status) implements RealtimePayload { }
    record WheelResult(UUID awardId, UUID drawId, int participantNumber, UUID prizeId, String prizeName, String category, java.util.List<ma.enset.bigbang.wheel.WheelDtos.PublicSegment> segments) implements RealtimePayload { }
    record WheelAvailability(java.util.List<ma.enset.bigbang.wheel.WheelDtos.PublicSegment> eligiblePrizes) implements RealtimePayload { }
    record WheelAdmin(UUID awardId, UUID drawId, UUID participantId, UUID prizeId, String prizeName, int remainingStock) implements RealtimePayload { }
    record PrizeWon(UUID awardId, UUID drawId, UUID prizeId, String prizeName, String message) implements RealtimePayload { }
    record QuizState(String status) implements RealtimePayload { }
    record PrivateNotification(String message) implements RealtimePayload { }
}
