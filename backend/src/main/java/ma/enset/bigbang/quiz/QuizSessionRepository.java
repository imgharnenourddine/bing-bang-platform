package ma.enset.bigbang.quiz;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizSessionRepository extends JpaRepository<QuizSession, UUID> {
    Optional<QuizSession> findByStatusIn(Collection<QuizStatus> statuses);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select q from QuizSession q where q.id = :id")
    Optional<QuizSession> findByIdForAnswer(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QuizSession q where q.id = :id")
    Optional<QuizSession> findByIdForUpdate(@Param("id") UUID id);
}
