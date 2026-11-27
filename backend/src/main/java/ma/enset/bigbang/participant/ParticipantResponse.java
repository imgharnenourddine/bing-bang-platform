package ma.enset.bigbang.participant;

import java.util.UUID;
import ma.enset.bigbang.auth.Role;

public record ParticipantResponse(UUID id, Integer participantNumber, String firstName, String lastName, Role role) {
    public static ParticipantResponse from(Participant participant) {
        return new ParticipantResponse(participant.getId(), participant.getParticipantNumber(),
                participant.getFirstName(), participant.getLastName(), Role.PARTICIPANT);
    }
}
