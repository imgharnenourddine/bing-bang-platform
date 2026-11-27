package ma.enset.bigbang.wheel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WheelRepository {
 private final JdbcTemplate jdbc; private final ObjectMapper json;
 public WheelRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
 private static final RowMapper<WheelDtos.Prize> PRIZE=(r,n)-> new WheelDtos.Prize(r.getObject("id",UUID.class),r.getString("name"),r.getString("description"),PrizeCategory.valueOf(r.getString("category")),r.getBigDecimal("base_weight"),r.getInt("initial_stock"),r.getInt("remaining_stock"),r.getBoolean("enabled"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
 private WheelDtos.Award award(ResultSet r,int row) throws SQLException {
  try { var segments=json.readValue(r.getString("segments_snapshot"),new TypeReference<List<WheelDtos.Segment>>(){});
   return new WheelDtos.Award(r.getObject("id",UUID.class),r.getObject("draw_id",UUID.class),r.getObject("participant_id",UUID.class),r.getInt("participant_number_snapshot"),r.getObject("prize_id",UUID.class),r.getString("prize_name_snapshot"),PrizeCategory.valueOf(r.getString("prize_category_snapshot")),r.getObject("awarded_by",UUID.class),r.getTimestamp("awarded_at").toInstant(),r.getBigDecimal("base_weight_snapshot"),r.getBigDecimal("effective_weight_snapshot"),r.getBigDecimal("winning_probability_snapshot"),r.getLong("progression_before"),r.getLong("progression_after"),segments);
  } catch (java.io.IOException ex) { throw new SQLException("Invalid award snapshot",ex); }
 }
 public WheelDtos.Config config(boolean lock) { return jdbc.queryForObject("SELECT * FROM wheel_configuration WHERE id=1"+(lock?" FOR UPDATE":""),(r,i)->new WheelDtos.Config(r.getBigDecimal("geometric_growth_factor"),r.getBigDecimal("maximum_premium_multiplier"),r.getLong("consecutive_standard_awards"),r.getTimestamp("updated_at").toInstant())); }
 public void updateConfig(WheelDtos.ConfigUpdate c) { jdbc.update("UPDATE wheel_configuration SET geometric_growth_factor=?, maximum_premium_multiplier=?, updated_at=CURRENT_TIMESTAMP WHERE id=1",c.geometricGrowthFactor(),c.maximumPremiumMultiplier()); }
 public void progression(long k) { jdbc.update("UPDATE wheel_configuration SET consecutive_standard_awards=?,updated_at=CURRENT_TIMESTAMP WHERE id=1",k); }
 public List<WheelDtos.Prize> prizes(boolean lock) { return jdbc.query("SELECT * FROM prizes ORDER BY id"+(lock?" FOR UPDATE":""),PRIZE); }
 public Optional<WheelDtos.Prize> prize(UUID id,boolean lock) { return jdbc.query("SELECT * FROM prizes WHERE id=?"+(lock?" FOR UPDATE":""),PRIZE,id).stream().findFirst(); }
 public void insertPrize(UUID id,WheelDtos.CreatePrize p) { jdbc.update("INSERT INTO prizes(id,name,description,category,base_weight,initial_stock,remaining_stock,enabled) VALUES (?,?,?,?,?,?,?,?)",id,p.name().strip(),p.description(),p.category().name(),p.baseWeight(),p.initialStock(),p.initialStock(),p.enabled()); }
 public void updatePrize(UUID id,String name,String description,PrizeCategory category,java.math.BigDecimal weight,boolean enabled) { jdbc.update("UPDATE prizes SET name=?,description=?,category=?,base_weight=?,enabled=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",name,description,category.name(),weight,enabled,id); }
 public void stock(UUID id,int value) { jdbc.update("UPDATE prizes SET remaining_stock=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",value,id); }
 public void audit(UUID id,UUID prize,int before,int delta,int after,String reason,UUID admin) { jdbc.update("INSERT INTO prize_stock_adjustments(id,prize_id,previous_stock,quantity_delta,resulting_stock,reason,adjusted_by) VALUES (?,?,?,?,?,?,?)",id,prize,before,delta,after,reason,admin); }
 public Optional<WheelDtos.Award> byDraw(UUID id) { return jdbc.query("SELECT * FROM prize_awards WHERE draw_id=?",this::award,id).stream().findFirst(); }
 public Optional<WheelDtos.Award> lastAward() { return jdbc.query("SELECT * FROM prize_awards ORDER BY awarded_at DESC,id DESC LIMIT 1",this::award).stream().findFirst(); }
 public void insertAward(UUID id,UUID draw,UUID participant,int number,UUID admin,WheelDtos.Segment winner,long before,long after,List<WheelDtos.Segment> segments) {
  try { String snapshot=json.writeValueAsString(segments);
   jdbc.update("""
    INSERT INTO prize_awards(id,draw_id,participant_id,prize_id,awarded_by,prize_name_snapshot,prize_category_snapshot,
    base_weight_snapshot,effective_weight_snapshot,winning_probability_snapshot,progression_before,progression_after,participant_number_snapshot,segments_snapshot)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)
    """,id,draw,participant,winner.prizeId(),admin,winner.name(),winner.category().name(),winner.baseWeight(),winner.effectiveWeight(),winner.probability(),before,after,number,snapshot);
  } catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize prize snapshot",ex); }
 }
 private ArrayList<Object> filters(StringBuilder sql,UUID participant,UUID prize,PrizeCategory category,UUID draw) {
  var args=new ArrayList<Object>(); if(participant!=null){sql.append(" AND participant_id=?");args.add(participant);} if(prize!=null){sql.append(" AND prize_id=?");args.add(prize);} if(category!=null){sql.append(" AND prize_category_snapshot=?");args.add(category.name());} if(draw!=null){sql.append(" AND draw_id=?");args.add(draw);} return args;
 }
 public WheelDtos.History history(UUID participant,UUID prize,PrizeCategory category,UUID draw,int page,int size) {
  var where=new StringBuilder(" WHERE 1=1");var args=filters(where,participant,prize,category,draw);
  long count=jdbc.queryForObject("SELECT count(*) FROM prize_awards"+where,Long.class,args.toArray());
  var params=new ArrayList<Object>(args);params.add(size);params.add((long)page*size);
  var awards=jdbc.query("SELECT * FROM prize_awards"+where+" ORDER BY awarded_at DESC,id DESC LIMIT ? OFFSET ?",this::award,params.toArray());
  return new WheelDtos.History(awards,page,size,count);
 }
}
