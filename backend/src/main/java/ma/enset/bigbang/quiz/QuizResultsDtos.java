package ma.enset.bigbang.quiz;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuizResultsDtos {
 private QuizResultsDtos() {}
 public record Personal(UUID quizId,String title,String status,long totalQuestions,long answeredQuestions,long correctAnswers,long incorrectAnswers,long unansweredQuestions,long totalScore,long maximumScore,BigDecimal accuracyPercentage,Instant finishedAt) {}
 public record Entry(long rank,int participantNumber,String firstName,String lastName,long totalScore,long answeredQuestions,long correctAnswers,long incorrectAnswers,long unansweredQuestions) {}
 public record Leaderboard(UUID quizId,String title,String status,long totalQuestions,long maximumScore,long totalParticipants,long participantsAnswered,long highestScore,BigDecimal averageScore,long firstPlaceCount,Instant finishedAt,List<Entry> leaderboard) {}
}
