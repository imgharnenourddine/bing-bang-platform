package ma.enset.bigbang.quiz;

import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "quiz_options")
public class QuizOption {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "question_id", nullable = false, updatable = false) private QuizQuestion question;
    @Column(name = "option_text", nullable = false, length = 1000) private String optionText;
    @Column(nullable = false) private int position;
    @Column(nullable = false) private boolean correct;
    protected QuizOption() { }
    public QuizOption(UUID id, QuizQuestion question, String optionText, int position, boolean correct) {
        if (position <= 0) throw new IllegalArgumentException("Position must be positive");
        this.id = Objects.requireNonNull(id); this.question = Objects.requireNonNull(question);
        this.optionText = QuizSession.requireText(optionText, 1000); this.position = position; this.correct = correct;
    }
    public UUID getId() { return id; }
    public QuizQuestion getQuestion() { return question; }
    public String getOptionText() { return optionText; }
    public int getPosition() { return position; }
    public boolean isCorrect() { return correct; }
}
