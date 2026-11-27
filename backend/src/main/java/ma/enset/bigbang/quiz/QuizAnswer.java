package ma.enset.bigbang.quiz;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import ma.enset.bigbang.participant.Participant;

@Entity
@Table(name = "quiz_answers")
public class QuizAnswer {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "question_id", nullable = false, updatable = false) private QuizQuestion question;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "participant_id", nullable = false, updatable = false) private Participant participant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "selected_option_id", nullable = false, updatable = false) private QuizOption selectedOption;
    @Column(name = "submitted_at", nullable = false, updatable = false) private Instant submittedAt;
    @Column(name = "awarded_points", nullable = false, updatable = false) private int awardedPoints;
    protected QuizAnswer() { }
    public QuizAnswer(UUID id, QuizQuestion question, Participant participant, QuizOption selectedOption, int awardedPoints) {
        if (awardedPoints < 0) throw new IllegalArgumentException("Awarded points must not be negative");
        this.id = Objects.requireNonNull(id); this.question = Objects.requireNonNull(question);
        this.participant = Objects.requireNonNull(participant); this.selectedOption = Objects.requireNonNull(selectedOption);
        if (!question.getId().equals(selectedOption.getQuestion().getId())) throw new IllegalArgumentException("Option does not belong to question");
        this.awardedPoints = awardedPoints; this.submittedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public QuizQuestion getQuestion() { return question; }
    public Participant getParticipant() { return participant; }
    public QuizOption getSelectedOption() { return selectedOption; }
    public Instant getSubmittedAt() { return submittedAt; }
    public int getAwardedPoints() { return awardedPoints; }
}
