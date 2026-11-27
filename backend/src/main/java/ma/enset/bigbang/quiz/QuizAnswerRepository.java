package ma.enset.bigbang.quiz;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {
    boolean existsByQuestionIdAndParticipantId(UUID questionId, UUID participantId);
    Optional<QuizAnswer> findByQuestionIdAndParticipantId(UUID questionId, UUID participantId);
    long countByQuestionId(UUID questionId);
    List<QuizAnswer> findByParticipantIdAndQuestionQuizSessionIdOrderByQuestionPositionAsc(UUID participantId, UUID sessionId);
    interface ParticipantScore { UUID getParticipantId(); Long getPoints(); }
    @Query("select a.participant.id as participantId, sum(a.awardedPoints) as points from QuizAnswer a where a.question.quizSession.id = :sessionId group by a.participant.id")
    List<ParticipantScore> sumPointsByParticipant(@Param("sessionId") UUID sessionId);
}
