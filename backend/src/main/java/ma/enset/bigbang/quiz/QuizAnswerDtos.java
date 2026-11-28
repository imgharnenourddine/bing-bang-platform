package ma.enset.bigbang.quiz;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class QuizAnswerDtos {
 private QuizAnswerDtos() { }
 public record Submit(@NotNull UUID questionId,@NotNull UUID optionId) { }
 public record Acknowledgment(UUID questionId,UUID selectedOptionId,String status,Instant submittedAt) { }
 public record Feedback(UUID questionId,UUID selectedOptionId,UUID correctOptionId,boolean hasAnswered,Boolean correct,int awardedPoints,int questionPoints) { }
 public record Statistics(UUID questionId,long submittedAnswers,long registeredParticipants) { }
}
