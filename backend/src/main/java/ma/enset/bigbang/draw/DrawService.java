package ma.enset.bigbang.draw;

import java.security.SecureRandom;
import java.util.UUID;
import ma.enset.bigbang.notification.EventType;
import ma.enset.bigbang.notification.RealtimeEvents;
import ma.enset.bigbang.notification.RealtimePayload;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DrawService {
    private final DrawRepository repository;
    private final RealtimeEvents realtime;
    private final SecureRandom random = new SecureRandom();
    public DrawService(DrawRepository repository, RealtimeEvents realtime) { this.repository = repository; this.realtime = realtime; }
    private DrawException conflict(String code, String message) { return new DrawException(HttpStatus.CONFLICT, code, message); }
    private DrawRecord find(UUID id) { return repository.find(id).orElseThrow(() -> new DrawException(HttpStatus.NOT_FOUND, "DRAW_NOT_FOUND", "Draw not found")); }
    private void requireNoActive() { if (repository.active().isPresent()) throw conflict("DRAW_ACTIVE", "An active draw must be resolved first"); }
    @Transactional
    public DrawDtos.Draw start(UUID adminId) {
        repository.lockControl();
        var cycle = repository.currentCycle();
        requireNoActive();
        var eligible = repository.eligible(cycle.id());
        if (eligible.isEmpty()) {
            if (repository.countParticipants() == 0) throw conflict("NO_PARTICIPANTS", "No participants have registered");
            throw conflict("NO_ELIGIBLE_PARTICIPANTS", "No eligible participants remain in this cycle");
        }
        UUID selected = eligible.get(random.nextInt(eligible.size()));
        UUID drawId = UUID.randomUUID();
        repository.insert(drawId, cycle.id(), selected, adminId);
        DrawRecord draw = find(drawId);
        var update = publicPayload(draw);
        realtime.drawUpdate(EventType.DRAW_STARTED, update);
        realtime.drawUpdate(EventType.DRAW_PARTICIPANT_SELECTED, update);
        realtime.drawUpdate(EventType.DRAW_STATE_UPDATED, update);
        realtime.drawAdmin(EventType.DRAW_PARTICIPANT_SELECTED, adminPayload(draw));
        realtime.drawSelected(selected, new RealtimePayload.DrawPrivate(draw.id(), draw.cycleNumber(), draw.participantNumber(), "You have been selected!"));
        return DrawDtos.toDto(draw);
    }
    @Transactional
    public DrawDtos.Draw cancel(UUID id, UUID adminId) {
        repository.lockControl();
        DrawRecord draw = find(id);
        if (draw.status() != DrawStatus.ACTIVE || repository.cancel(id, adminId) != 1)
            throw conflict("INVALID_DRAW_TRANSITION", "Only an active draw can be cancelled");
        draw = find(id);
        realtime.drawUpdate(EventType.DRAW_CANCELLED, publicPayload(draw));
        realtime.drawUpdate(EventType.DRAW_STATE_UPDATED, publicPayload(draw));
        realtime.drawAdmin(EventType.DRAW_CANCELLED, adminPayload(draw));
        return DrawDtos.toDto(draw);
    }
    /** Must run in the prize allocation transaction, after its unique draw award is persisted. */
    @Transactional(propagation = Propagation.MANDATORY)
    public DrawDtos.Draw completeAfterPrizeAllocation(UUID id) {
        repository.lockControl();
        DrawRecord draw = find(id);
        if (draw.status() != DrawStatus.ACTIVE || repository.complete(id) != 1)
            throw conflict("INVALID_DRAW_TRANSITION", "Only an active draw can be completed");
        draw = find(id);
        realtime.drawUpdate(EventType.DRAW_COMPLETED, publicPayload(draw));
        realtime.drawUpdate(EventType.DRAW_STATE_UPDATED, publicPayload(draw));
        realtime.drawAdmin(EventType.DRAW_COMPLETED, adminPayload(draw));
        return DrawDtos.toDto(draw);
    }
    /** Called by F05 inside its transaction before allocating; locks the shared control row. */
    @Transactional(propagation = Propagation.MANDATORY)
    public DrawDtos.Draw lockActiveForPrizeAllocation() {
        repository.lockControl();
        return repository.active().map(DrawDtos::toDto)
                .orElseThrow(() -> conflict("NO_ACTIVE_DRAW", "There is no active draw"));
    }
    @Transactional
    public DrawDtos.ResetResult reset(UUID adminId, DrawDtos.ResetRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirmed()) || request.expectedCycleNumber() == null || request.expectedCycleNumber() < 1)
            throw new DrawException(HttpStatus.BAD_REQUEST, "RESET_CONFIRMATION_REQUIRED", "Confirmation and a positive expected cycle are required");
        repository.lockControl();
        var cycle = repository.currentCycle();
        if (cycle.cycleNumber() != request.expectedCycleNumber()) throw conflict("STALE_CYCLE", "The current cycle has changed");
        requireNoActive();
        int next = Math.addExact(cycle.cycleNumber(), 1);
        repository.closeCycle(cycle.id());
        repository.createCycle(next, adminId);
        long total = repository.countParticipants();
        realtime.drawReset(new RealtimePayload.DrawReset(cycle.cycleNumber(), next));
        realtime.drawUpdate(EventType.DRAW_STATE_UPDATED, new RealtimePayload.DrawUpdate(null, next, null, null));
        return new DrawDtos.ResetResult(cycle.cycleNumber(), next, total, 0, total);
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DrawDtos.Status status() {
        var cycle = repository.currentCycle();
        long total = repository.countParticipants(), selected = repository.countSelected(cycle.id());
        var active = repository.active().map(DrawDtos::toDto).orElse(null);
        return new DrawDtos.Status(cycle.cycleNumber(), total, selected, total - selected, active, active == null && total > selected, active == null);
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DrawDtos.PublicState current() {
        var cycle = repository.currentCycle();
        var active = repository.active().orElse(null);
        return new DrawDtos.PublicState(cycle.cycleNumber(), active != null,
                active == null ? null : active.participantNumber(), active == null ? null : active.status());
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DrawDtos.MyState myState(UUID participantId) {
        var cycle = repository.currentCycle();
        var selected = repository.selectedInCycle(cycle.id(), participantId).orElse(null);
        return new DrawDtos.MyState(cycle.cycleNumber(), selected != null, selected != null && selected.status() == DrawStatus.ACTIVE,
                selected == null ? null : selected.id(), selected == null ? null : selected.status());
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DrawDtos.History history(Integer cycle, DrawStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (cycle != null && cycle < 1))
            throw new DrawException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Invalid history filters or pagination");
        var results = repository.history(cycle, status, page, size).stream().map(DrawDtos::toDto).toList();
        return new DrawDtos.History(results, page, size, repository.historyCount(cycle, status));
    }
    private RealtimePayload.DrawUpdate publicPayload(DrawRecord d) {
        return new RealtimePayload.DrawUpdate(d.id(), d.cycleNumber(), d.status().name(), d.participantNumber());
    }
    private RealtimePayload.DrawAdmin adminPayload(DrawRecord d) {
        return new RealtimePayload.DrawAdmin(d.id(), d.cycleNumber(), d.status().name(), d.participantId(), d.participantNumber(), d.firstName(), d.lastName());
    }
}
