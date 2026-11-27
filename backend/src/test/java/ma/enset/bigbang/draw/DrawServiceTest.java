package ma.enset.bigbang.draw;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.enset.bigbang.notification.EventType;
import ma.enset.bigbang.notification.RealtimeEvents;
import ma.enset.bigbang.notification.RealtimePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DrawServiceTest {
    private final DrawRepository repo = mock(DrawRepository.class);
    private final RealtimeEvents realtime = mock(RealtimeEvents.class);
    private final DrawService service = new DrawService(repo, realtime);
    private final UUID admin = UUID.randomUUID(), participant = UUID.randomUUID(), id = UUID.randomUUID();
    private DrawRecord draw(DrawStatus status) {
        return new DrawRecord(id, 1L, 1, participant, 7, "First", "Last", admin, status, Instant.now(), null, null, null);
    }
    @BeforeEach void init() { when(repo.currentCycle()).thenReturn(new DrawCycle(1L, 1, Instant.now())); }
    @Test void startsWithOnlyEligibleParticipantAndPrivateNotification() {
        when(repo.eligible(1L)).thenReturn(List.of(participant));
        when(repo.find(any())).thenReturn(Optional.of(draw(DrawStatus.ACTIVE)));
        var result = service.start(admin);
        assertEquals(DrawStatus.ACTIVE, result.status());
        verify(repo).insert(any(), eq(1L), eq(participant), eq(admin));
        verify(repo).lockControl();
        verify(realtime).drawSelected(eq(participant), any(RealtimePayload.DrawPrivate.class));
        verify(realtime).drawUpdate(eq(EventType.DRAW_PARTICIPANT_SELECTED), argThat(p -> p.participantNumber() == 7));
    }
    @Test void rejectsActiveAndEmptyPopulations() {
        when(repo.active()).thenReturn(Optional.of(draw(DrawStatus.ACTIVE)));
        assertEquals("DRAW_ACTIVE", assertThrows(DrawException.class, () -> service.start(admin)).code());
        when(repo.active()).thenReturn(Optional.empty());
        when(repo.eligible(1L)).thenReturn(List.of());
        when(repo.countParticipants()).thenReturn(0L, 1L);
        assertEquals("NO_PARTICIPANTS", assertThrows(DrawException.class, () -> service.start(admin)).code());
        assertEquals("NO_ELIGIBLE_PARTICIPANTS", assertThrows(DrawException.class, () -> service.start(admin)).code());
        verify(repo, never()).insert(any(), anyLong(), any(), any());
    }
    @Test void resetRejectsStaleOrActiveAndPublishesAfterScheduling() {
        assertEquals("RESET_CONFIRMATION_REQUIRED", assertThrows(DrawException.class, () -> service.reset(admin, new DrawDtos.ResetRequest(false, 1))).code());
        assertEquals("STALE_CYCLE", assertThrows(DrawException.class, () -> service.reset(admin, new DrawDtos.ResetRequest(true, 2))).code());
        when(repo.active()).thenReturn(Optional.of(draw(DrawStatus.ACTIVE)), Optional.empty());
        assertEquals("DRAW_ACTIVE", assertThrows(DrawException.class, () -> service.reset(admin, new DrawDtos.ResetRequest(true, 1))).code());
        when(repo.countParticipants()).thenReturn(3L);
        var result = service.reset(admin, new DrawDtos.ResetRequest(true, 1));
        assertEquals(2, result.cycleNumber()); assertEquals(3, result.eligibleCount());
        verify(repo).closeCycle(1L); verify(repo).createCycle(2, admin);
        verify(realtime).drawReset(new RealtimePayload.DrawReset(1, 2));
    }
    @Test void cancelKeepsHistoryAndCompletedCannotBeCancelled() {
        when(repo.find(id)).thenReturn(Optional.of(draw(DrawStatus.ACTIVE)), Optional.of(draw(DrawStatus.CANCELLED)));
        when(repo.cancel(id, admin)).thenReturn(1);
        assertEquals(DrawStatus.CANCELLED, service.cancel(id, admin).status());
        verify(realtime).drawUpdate(eq(EventType.DRAW_CANCELLED), any());
        assertEquals("INVALID_DRAW_TRANSITION", assertThrows(DrawException.class, () -> service.cancel(id, admin)).code());
    }
    @Test void completionTransitionsOnlyAnActiveDraw() {
        when(repo.find(id)).thenReturn(Optional.of(draw(DrawStatus.ACTIVE)), Optional.of(draw(DrawStatus.COMPLETED)));
        when(repo.complete(id)).thenReturn(1);
        assertEquals(DrawStatus.COMPLETED, service.completeAfterPrizeAllocation(id).status());
        verify(repo).lockControl();
        verify(realtime).drawUpdate(eq(EventType.DRAW_COMPLETED), any());
    }
    @Test void personalStateResolvesOnlyRequestedPrincipal() {
        when(repo.selectedInCycle(1, participant)).thenReturn(Optional.of(draw(DrawStatus.CANCELLED)));
        var state = service.myState(participant);
        assertTrue(state.selectedInCurrentCycle()); assertFalse(state.currentlySelected());
        verify(repo).selectedInCycle(1, participant);
    }
}
