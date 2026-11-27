package ma.enset.bigbang.quiz;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {
    List<QuizQuestion> findByQuizSessionIdOrderByPositionAsc(UUID sessionId);
    Optional<QuizQuestion> findByIdAndQuizSessionId(UUID id, UUID sessionId);
    Optional<QuizQuestion> findFirstByQuizSessionIdAndPositionGreaterThanOrderByPositionAsc(UUID sessionId, int position);
    long countByQuizSessionId(UUID sessionId);
}
