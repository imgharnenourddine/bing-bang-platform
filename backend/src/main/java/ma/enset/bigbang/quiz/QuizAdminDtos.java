package ma.enset.bigbang.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuizAdminDtos {
    private QuizAdminDtos() { }
    public record CreateQuiz(@NotBlank @Size(max=200) String title) { }
    public record UpdateQuiz(@NotBlank @Size(max=200) String title, @NotNull @PositiveOrZero Long expectedVersion) { }
    public record OptionInput(@NotBlank @Size(max=1000) String text, @NotNull Boolean correct) { }
    public record QuestionInput(@NotBlank @Size(max=2000) String text, @Positive Integer durationSeconds,
            @Positive Integer points, @NotNull @Size(min=2,max=10) List<@NotNull @Valid OptionInput> options,
            @NotNull @PositiveOrZero Long expectedVersion) { }
    public record Reorder(@NotNull @Size(max=10000) List<@NotNull UUID> questionIds, @NotNull @PositiveOrZero Long expectedVersion) { }
    public record Quiz(UUID id,String title,QuizStatus status,long version,long questionCount,Instant createdAt,Instant updatedAt) { }
    public record Option(UUID id,String text,int position,boolean correct) { }
    public record Question(UUID id,String text,int position,int durationSeconds,int points,List<Option> options,Instant createdAt,Instant updatedAt,long quizVersion) { }
    public record Readiness(boolean ready,long questionCount,List<String> issues) { }
}
