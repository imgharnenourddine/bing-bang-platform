package ma.enset.bigbang.notification;

import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Call within the business transaction; no messages are sent if it rolls back. */
@Service
public class RealtimeEvents {
    enum Target { EVENT, DRAW, WHEEL, QUIZ, ADMIN, PARTICIPANT }
    record Pending(Target target, UUID recipient, EventEnvelope<? extends RealtimePayload> event) { }
    private final ApplicationEventPublisher events;
    private final ma.enset.bigbang.participant.ParticipantRepository participants;
    public RealtimeEvents(ApplicationEventPublisher events, ma.enset.bigbang.participant.ParticipantRepository participants) {
        this.events = events; this.participants = participants;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void event(RealtimePayload.EventState payload) { submit(Target.EVENT, null, EventType.EVENT_STATE_UPDATED, payload); }
    @Transactional(propagation = Propagation.MANDATORY)
    public void participantRegistered(RealtimePayload.ParticipantRegistered payload) { submit(Target.ADMIN, null, EventType.PARTICIPANT_REGISTERED, payload); }
    @Transactional(propagation = Propagation.MANDATORY)
    public void draw(EventType type, RealtimePayload.DrawState payload) {
        require(type, EnumSet.of(EventType.DRAW_STARTED, EventType.DRAW_PARTICIPANT_SELECTED, EventType.DRAW_COMPLETED));
        submit(Target.DRAW, null, type, payload);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void drawUpdate(EventType type, RealtimePayload.DrawUpdate payload) {
        require(type, EnumSet.of(EventType.DRAW_STARTED, EventType.DRAW_PARTICIPANT_SELECTED,
                EventType.DRAW_COMPLETED, EventType.DRAW_CANCELLED, EventType.DRAW_STATE_UPDATED));
        submit(Target.DRAW, null, type, payload);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void drawReset(RealtimePayload.DrawReset payload) { submit(Target.DRAW, null, EventType.DRAW_RESET, payload); }
    @Transactional(propagation = Propagation.MANDATORY)
    public void drawAdmin(EventType type, RealtimePayload.DrawAdmin payload) {
        require(type, EnumSet.of(EventType.DRAW_PARTICIPANT_SELECTED, EventType.DRAW_CANCELLED, EventType.DRAW_COMPLETED));
        submit(Target.ADMIN, null, type, payload);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void drawSelected(UUID participantId, RealtimePayload.DrawPrivate payload) {
        Objects.requireNonNull(participantId);
        if (!participants.existsById(participantId)) throw new IllegalArgumentException("Participant does not exist");
        submit(Target.PARTICIPANT, participantId, EventType.PARTICIPANT_SELECTED, payload);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void wheel(EventType type, RealtimePayload.WheelState payload) {
        require(type, EnumSet.of(EventType.WHEEL_STARTED, EventType.WHEEL_RESULT, EventType.PRIZE_AWARDED));
        submit(Target.WHEEL, null, type, payload);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void wheelResult(EventType type, RealtimePayload.WheelResult payload) {
        require(type, EnumSet.of(EventType.WHEEL_STARTED, EventType.WHEEL_RESULT, EventType.WHEEL_STATE_UPDATED));
        submit(Target.WHEEL, null, type, payload);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void wheelAdmin(EventType type, RealtimePayload.WheelAdmin payload) {
        require(type, EnumSet.of(EventType.PRIZE_AWARDED, EventType.PRIZE_INVENTORY_UPDATED));
        submit(Target.ADMIN, null, type, payload);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void wheelAvailability(RealtimePayload.WheelAvailability payload) { submit(Target.WHEEL, null, EventType.WHEEL_STATE_UPDATED, payload); }
    @Transactional(propagation = Propagation.MANDATORY)
    public void wheelInventory(RealtimePayload.WheelAdmin payload) { submit(Target.ADMIN, null, EventType.PRIZE_INVENTORY_UPDATED, payload); }
    @Transactional(propagation = Propagation.MANDATORY)
    public void prizeWon(UUID participantId, RealtimePayload.PrizeWon payload) {
        Objects.requireNonNull(participantId);
        if (!participants.existsById(participantId)) throw new IllegalArgumentException("Participant does not exist");
        submit(Target.PARTICIPANT, participantId, EventType.PRIZE_WON, payload);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void quiz(EventType type, RealtimePayload.QuizState payload) {
        require(type, EnumSet.of(EventType.QUIZ_OPENED, EventType.QUESTION_OPENED, EventType.QUESTION_CLOSED, EventType.LEADERBOARD_UPDATED, EventType.QUIZ_FINISHED));
        submit(Target.QUIZ, null, type, payload);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void privateParticipant(UUID id, EventType type, RealtimePayload.PrivateNotification payload) {
        require(type, EnumSet.of(EventType.PARTICIPANT_SELECTED, EventType.PRIZE_WON));
        Objects.requireNonNull(id);
        if (!participants.existsById(id)) throw new IllegalArgumentException("Participant does not exist");
        submit(Target.PARTICIPANT, id, type, payload);
    }
    private void submit(Target target, UUID recipient, EventType type, RealtimePayload payload) {
        events.publishEvent(new Pending(target, recipient, EventEnvelope.create(type, Objects.requireNonNull(payload))));
    }
    private static void require(EventType type, EnumSet<EventType> allowed) {
        if (!allowed.contains(type)) throw new IllegalArgumentException("Event type does not match destination");
    }
}
