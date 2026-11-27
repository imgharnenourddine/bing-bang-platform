package ma.enset.bigbang.quiz;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "quiz_sessions")
public class QuizSession {
    @Id private UUID id;
    @Column(nullable = false, length = 200) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private QuizStatus status;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "current_question_id") private QuizQuestion currentQuestion;
    @Column(name = "question_opened_at") private Instant questionOpenedAt;
    @Column(name = "question_deadline") private Instant questionDeadline;
    @Column(name = "opened_at") private Instant openedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;
    protected QuizSession() { }
    public QuizSession(UUID id, String title, Instant now) {
        this.id = Objects.requireNonNull(id); this.title = requireText(title, 200); this.status = QuizStatus.DRAFT;
        this.createdAt = Objects.requireNonNull(now); this.updatedAt = now;
    }
    static String requireText(String text, int max) {
        if (text == null || text.isBlank() || text.strip().length() > max) throw new IllegalArgumentException("Invalid text");
        return text.strip();
    }
    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public QuizStatus getStatus() { return status; }
    public QuizQuestion getCurrentQuestion() { return currentQuestion; }
    public Instant getQuestionOpenedAt() { return questionOpenedAt; }
    public Instant getQuestionDeadline() { return questionDeadline; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
