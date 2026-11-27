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
    public void wheel(EventType type, RealtimePayload.WheelState payload) {
        require(type, EnumSet.of(EventType.WHEEL_STARTED, EventType.WHEEL_RESULT, EventType.PRIZE_AWARDED));
        submit(Target.WHEEL, null, type, payload);
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
        events.publishEvent(new Pending(target, recipient, EventEnvelope.create(type, Objects.requireNonNull(payload)));
    }
    private static void require(EventType type, EnumSet<EventType> allowed) {
        if (!allowed.contains(type)) throw new IllegalArgumentException("Event type does not match destination");
    }
}
