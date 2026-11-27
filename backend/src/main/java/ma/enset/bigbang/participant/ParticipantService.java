package ma.enset.bigbang.participant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ParticipantService {
    private final ParticipantRepository participants;
    private final JdbcTemplate jdbc;
    public ParticipantService(ParticipantRepository participants, JdbcTemplate jdbc) {
        this.participants = participants; this.jdbc = jdbc;
    }
    public record Registration(ParticipantResponse participant, boolean created) { }

    @Transactional
    public Registration register(String sessionId, RegistrationRequest request) {
        String digest = digest(sessionId);
        // The lock and mapping are shared by all application instances. They bind retries to
        // the pre-authentication session even while the session ID is being rotated.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0)) IS NULL", Boolean.class, digest);
        var previous = jdbc.query("SELECT participant_id FROM participant_registration_sessions WHERE session_digest = ?",
                (rs, row) -> rs.getObject(1, UUID.class), digest);
        if (!previous.isEmpty()) return new Registration(findById(previous.get(0)), false);
        Integer number = jdbc.queryForObject("SELECT nextval('participant_number_seq')", Integer.class);
        if (number == null) throw new IllegalStateException("Participant sequence returned no value");
        Participant participant = participants.saveAndFlush(new Participant(UUID.randomUUID(), number,
                request.firstName(), request.lastName(), Instant.now()));
        jdbc.update("INSERT INTO participant_registration_sessions (session_digest, participant_id) VALUES (?, ?)", digest, participant.getId());
        return new Registration(ParticipantResponse.from(participant), true);
    }
    @Transactional(readOnly = true)
    public ParticipantResponse findById(UUID id) {
        return participants.findById(id).map(ParticipantResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found"));
    }
    @Transactional(readOnly = true)
    public Page<ParticipantResponse> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        return participants.findAll(PageRequest.of(page, size, Sort.by("participantNumber").ascending())).map(ParticipantResponse::from);
    }
    @Transactional(readOnly = true)
    public long count() { return participants.count(); }
    private static String digest(String sessionId) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
