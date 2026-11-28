package ma.enset.bigbang.quiz;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "quiz_session_id", nullable = false, updatable = false) private QuizSession quizSession;
    @Column(name = "question_text", nullable = false, length = 2000) private String questionText;
    @Column(nullable = false) private int position;
    @Column(name = "duration_seconds", nullable = false) private int durationSeconds;
    @Column(nullable = false) private int points;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected QuizQuestion() { }
    public QuizQuestion(UUID id, QuizSession quizSession, String text, int position, int durationSeconds, int points, Instant now) {
        if (position <= 0 || durationSeconds <= 0 || points <= 0) throw new IllegalArgumentException("Position, duration and points must be positive");
        this.id = Objects.requireNonNull(id); this.quizSession = Objects.requireNonNull(quizSession);
        this.questionText = QuizSession.requireText(text, 2000); this.position = position;
        this.durationSeconds = durationSeconds; this.points = points; this.createdAt = Objects.requireNonNull(now); this.updatedAt = now;
    }
    void revise(String text, int duration, int score, Instant now) {
        if (duration <= 0 || score <= 0) throw new IllegalArgumentException("Duration and points must be positive");
        questionText = QuizSession.requireText(text, 2000); durationSeconds = duration; points = score; updatedAt = now;
    }
    public UUID getId() { return id; }
    public QuizSession getQuizSession() { return quizSession; }
    public String getQuestionText() { return questionText; }
    public int getPosition() { return position; }
    public int getDurationSeconds() { return durationSeconds; }
    public int getPoints() { return points; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
