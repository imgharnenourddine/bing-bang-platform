package ma.enset.bigbang.participant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "participants")
public class Participant {
    @Id private UUID id;
    @Column(name = "participant_number", nullable = false, unique = true) private Integer participantNumber;
    @Column(name = "first_name", nullable = false, length = 100) private String firstName;
    @Column(name = "last_name", nullable = false, length = 100) private String lastName;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected Participant() { }
    public Participant(UUID id, int number, String firstName, String lastName, Instant createdAt) {
        this.id = id; this.participantNumber = number; this.firstName = firstName;
        this.lastName = lastName; this.createdAt = createdAt;
    }
    public UUID getId() { return id; }
    public Integer getParticipantNumber() { return participantNumber; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Instant getCreatedAt() { return createdAt; }
}
