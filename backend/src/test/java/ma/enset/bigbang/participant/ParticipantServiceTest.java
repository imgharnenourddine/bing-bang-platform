package ma.enset.bigbang.participant;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ParticipantServiceTest {
    @Test void newSessionObtainsSequenceValueAndStoresDigest() {
        var repository = mock(ParticipantRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any())).thenReturn(true);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(), any())).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var realtime = mock(ma.enset.bigbang.notification.RealtimeEvents.class);
        var result = new ParticipantService(repository, jdbc, realtime).register("session-secret", new RegistrationRequest("  أحمد ", "Alami"));
        assertTrue(result.created());
        verify(realtime).participantRegistered(any());
        assertEquals(1, result.participant().participantNumber());
        assertEquals("أحمد", result.participant().firstName());
        var digest = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(contains("participant_registration_sessions"), digest.capture(), any());
        assertNotEquals("session-secret", digest.getValue());
        assertEquals(64, digest.getValue().length());
    }
    @Test void existingRegistrationDoesNotPublishAnotherEvent() {
        var repository = mock(ParticipantRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var realtime = mock(ma.enset.bigbang.notification.RealtimeEvents.class);
        var id = UUID.randomUUID();
        var participant = new Participant(id, 7, "A", "B", java.time.Instant.now());
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any())).thenReturn(true);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(), any())).thenReturn(List.of(id));
        when(repository.findById(id)).thenReturn(java.util.Optional.of(participant));
        var result = new ParticipantService(repository, jdbc, realtime).register("existing", new RegistrationRequest("A", "B"));
        assertFalse(result.created());
        verifyNoInteractions(realtime);
    }
}
