package ma.enset.bigbang.quiz;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizOptionRepository extends JpaRepository<QuizOption, UUID> {
    List<QuizOption> findByQuestionQuizSessionIdOrderByQuestionPositionAscPositionAsc(UUID sessionId);
    List<QuizOption> findByQuestionIdOrderByPositionAsc(UUID questionId);
    Optional<QuizOption> findByQuestionIdAndCorrectTrue(UUID questionId);
    boolean existsByIdAndQuestionId(UUID id, UUID questionId);
}
