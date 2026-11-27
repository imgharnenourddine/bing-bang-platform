package ma.enset.bigbang.draw;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DrawDtos {
    private DrawDtos() { }
    public record Participant(UUID id, int participantNumber, String firstName, String lastName) { }
    public record Draw(UUID id, int cycleNumber, DrawStatus status, Participant participant, Instant createdAt,
                       Instant completedAt, Instant cancelledAt, UUID selectedBy, UUID cancelledBy) { }
    public record Status(int cycleNumber, long registeredCount, long selectedCount, long eligibleCount,
                         Draw activeDraw, boolean canStartDraw, boolean canReset) { }
    public record PublicState(int cycleNumber, boolean active, Integer participantNumber, DrawStatus drawStatus) { }
    public record MyState(int cycleNumber, boolean selectedInCurrentCycle, boolean currentlySelected, UUID drawId, DrawStatus drawStatus) { }
    public record ResetRequest(@NotNull @AssertTrue Boolean confirmed, @NotNull @Positive Integer expectedCycleNumber) { }
    public record ResetResult(int previousCycleNumber, int cycleNumber, long registeredCount, long selectedCount, long eligibleCount) { }
    public record History(List<Draw> content, int page, int size, long totalElements) { }
    public static Draw toDto(DrawRecord draw) {
        return new Draw(draw.id(), draw.cycleNumber(), draw.status(),
                new Participant(draw.participantId(), draw.participantNumber(), draw.firstName(), draw.lastName()),
                draw.createdAt(), draw.completedAt(), draw.cancelledAt(), draw.selectedBy(), draw.cancelledBy());
    }
}
