package ma.enset.bigbang.quiz;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuizLifecycleDtos {
 private QuizLifecycleDtos() { }
 public record VersionRequest(@NotNull @PositiveOrZero Long expectedVersion) { }
 public record QuestionRequest(@NotNull UUID expectedQuestionId,@NotNull @PositiveOrZero Long expectedVersion) { }
 public record AdminState(UUID quizId,String title,QuizStatus status,UUID currentQuestionId,Integer currentQuestionPosition,long totalQuestions,Instant questionOpenedAt,Instant questionDeadline,Instant questionClosedAt,Instant quizOpenedAt,Instant quizFinishedAt,Instant serverTime,boolean hasNextQuestion,List<String> allowedActions,long lifecycleVersion) { }
 public record PublicOption(UUID id,String text,int position) { }
 public record ParticipantState(String title,QuizStatus status,UUID currentQuestionId,Integer currentQuestionPosition,long totalQuestions,String questionText,List<PublicOption> options,Instant questionOpenedAt,Instant questionDeadline,Instant serverTime,boolean hasAnswered,UUID selectedOptionId,boolean canSubmitAnswer,UUID quizId,long lifecycleVersion) { }
}
