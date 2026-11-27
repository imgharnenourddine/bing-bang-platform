package ma.enset.bigbang.draw;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DrawRepository {
    private final JdbcTemplate jdbc;
    private static final String SELECT = """
        SELECT d.*, c.cycle_number, p.participant_number, p.first_name, p.last_name
        FROM draws d JOIN draw_cycles c ON c.id=d.cycle_id JOIN participants p ON p.id=d.participant_id
        """;
    private static final RowMapper<DrawRecord> MAPPER = (rs, row) -> new DrawRecord(
        rs.getObject("id", UUID.class), rs.getLong("cycle_id"), rs.getInt("cycle_number"),
        rs.getObject("participant_id", UUID.class), rs.getInt("participant_number"), rs.getString("first_name"),
        rs.getString("last_name"), rs.getObject("selected_by", UUID.class), DrawStatus.valueOf(rs.getString("status")),
        instant(rs, "created_at"), instant(rs, "completed_at"), instant(rs, "cancelled_at"), rs.getObject("cancelled_by", UUID.class));
    public DrawRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        var value = rs.getTimestamp(name); return value == null ? null : value.toInstant();
    }
    public void lockControl() { jdbc.queryForObject("SELECT id FROM draw_control WHERE id=1 FOR UPDATE", Integer.class); }
    public DrawCycle currentCycle() {
        return jdbc.queryForObject("SELECT id, cycle_number, created_at FROM draw_cycles WHERE closed_at IS NULL", (rs, i) ->
                new DrawCycle(rs.getLong("id"), rs.getInt("cycle_number"), rs.getTimestamp("created_at").toInstant()));
    }
    public Optional<DrawRecord> active() { return jdbc.query(SELECT + " WHERE d.status='ACTIVE'", MAPPER).stream().findFirst(); }
    public Optional<DrawRecord> find(UUID id) { return jdbc.query(SELECT + " WHERE d.id=?", MAPPER, id).stream().findFirst(); }
    public Optional<DrawRecord> selectedInCycle(long cycleId, UUID participantId) {
        return jdbc.query(SELECT + " WHERE d.cycle_id=? AND d.participant_id=?", MAPPER, cycleId, participantId).stream().findFirst();
    }
    public long countParticipants() { return jdbc.queryForObject("SELECT count(*) FROM participants", Long.class); }
    public long countSelected(long cycleId) { return jdbc.queryForObject("SELECT count(*) FROM draws WHERE cycle_id=?", Long.class, cycleId); }
    public List<UUID> eligible(long cycleId) {
        return jdbc.query("SELECT p.id FROM participants p WHERE NOT EXISTS (SELECT 1 FROM draws d WHERE d.cycle_id=? AND d.participant_id=p.id) ORDER BY p.id",
                (rs, i) -> rs.getObject(1, UUID.class), cycleId);
    }
    public void insert(UUID id, long cycleId, UUID participant, UUID admin) {
        jdbc.update("INSERT INTO draws(id,cycle_id,participant_id,selected_by,status) VALUES (?,?,?,?,'ACTIVE')", id, cycleId, participant, admin);
    }
    public int cancel(UUID id, UUID admin) {
        return jdbc.update("UPDATE draws SET status='CANCELLED', cancelled_at=CURRENT_TIMESTAMP, cancelled_by=? WHERE id=? AND status='ACTIVE'", admin, id);
    }
    public int complete(UUID id) {
        return jdbc.update("UPDATE draws SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP WHERE id=? AND status='ACTIVE'", id);
    }
    public void closeCycle(long id) { jdbc.update("UPDATE draw_cycles SET closed_at=CURRENT_TIMESTAMP WHERE id=? AND closed_at IS NULL", id); }
    public void createCycle(int number, UUID admin) { jdbc.update("INSERT INTO draw_cycles(cycle_number,created_by) VALUES (?,?)", number, admin); }
    public List<DrawRecord> history(Integer cycle, DrawStatus status, int page, int size) {
        var sql = new StringBuilder(SELECT).append(" WHERE 1=1");
        var args = filters(sql, cycle, status);
        sql.append(" ORDER BY d.created_at DESC, d.id DESC LIMIT ? OFFSET ?"); args.add(size); args.add((long) page * size);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }
    public long historyCount(Integer cycle, DrawStatus status) {
        var sql = new StringBuilder("SELECT count(*) FROM draws d JOIN draw_cycles c ON c.id=d.cycle_id WHERE 1=1");
        var args = filters(sql, cycle, status);
        return jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    }
    private static ArrayList<Object> filters(StringBuilder sql, Integer cycle, DrawStatus status) {
        var args = new ArrayList<Object>();
        if (cycle != null) { sql.append(" AND c.cycle_number=?"); args.add(cycle); }
        if (status != null) { sql.append(" AND d.status=?"); args.add(status.name()); }
        return args;
    }
}
