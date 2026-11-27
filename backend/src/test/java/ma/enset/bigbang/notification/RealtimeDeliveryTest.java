package ma.enset.bigbang.notification;

import java.util.UUID;
import ma.enset.bigbang.auth.Role;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeDeliveryTest {
    @Test void participantMessagesAreRoutedByNamespacedUuid() {
        var template = mock(SimpMessagingTemplate.class);
        var id = UUID.randomUUID();
        var payload = new RealtimePayload.PrivateNotification("Selected");
        var event = EventEnvelope.create(EventType.PARTICIPANT_SELECTED, payload);
        new RealtimeDelivery(template).deliver(new RealtimeEvents.Pending(RealtimeEvents.Target.PARTICIPANT, id, event));
        verify(template).convertAndSendToUser("PARTICIPANT:" + id, "/queue/notifications", event);
        verifyNoMoreInteractions(template);
        assertNotEquals(RealtimeIdentity.name(Role.ADMIN, id), RealtimeIdentity.name(Role.PARTICIPANT, id));
    }
    @Test void envelopesUseUniqueIdsAndUtcTimestamps() {
        var payload = new RealtimePayload.QuizState("OPEN");
        var first = EventEnvelope.create(EventType.QUIZ_OPENED, payload);
        var second = EventEnvelope.create(EventType.QUIZ_OPENED, payload);
        assertNotEquals(first.eventId(), second.eventId());
        assertTrue(first.timestamp().toString().endsWith("Z"));
        assertEquals(1, first.version());
    }
    @Test void envelopeSerializesWithExpectedFields() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var event = EventEnvelope.create(EventType.QUIZ_OPENED, new RealtimePayload.QuizState("OPEN"));
        var json = mapper.readTree(mapper.writeValueAsBytes(event));
        assertEquals("QUIZ_OPENED", json.get("type").asText());
        assertEquals(event.eventId().toString(), json.get("eventId").asText());
        assertEquals(1, json.get("version").asInt());
        assertEquals("OPEN", json.get("payload").get("status").asText());
    }
    @Test void failureAfterCommitDoesNotEscape() {
        var template = mock(SimpMessagingTemplate.class);
        doThrow(new IllegalStateException()).when(template).convertAndSend(eq("/topic/admin"), any(EventEnvelope.class));
        var event = EventEnvelope.create(EventType.PARTICIPANT_REGISTERED, new RealtimePayload.ParticipantRegistered(UUID.randomUUID(), 1, "A", "B"));
        assertDoesNotThrow(() -> new RealtimeDelivery(template).deliver(new RealtimeEvents.Pending(RealtimeEvents.Target.ADMIN, null, event)));
    }
}
