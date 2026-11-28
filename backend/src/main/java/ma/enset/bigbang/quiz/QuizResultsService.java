package ma.enset.bigbang.quiz;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizResultsService {
 private final QuizSessionRepository sessions;
 private final JdbcTemplate jdbc;
 public QuizResultsService(QuizSessionRepository sessions, JdbcTemplate jdbc) { this.sessions=sessions; this.jdbc=jdbc; }
 private QuizSession finished() {
  var all=sessions.findAll();
  if(all.isEmpty()) throw new QuizAdminException(HttpStatus.NOT_FOUND,"QUIZ_NOT_FOUND","Quiz not found.");
  if(all.size()!=1) throw new QuizAdminException(HttpStatus.CONFLICT,"AMBIGUOUS_QUIZ","Quiz state unavailable.");
  var quiz=all.getFirst();
  if(quiz.getStatus()!=QuizStatus.FINISHED) throw new QuizAdminException(HttpStatus.CONFLICT,"QUIZ_RESULTS_NOT_AVAILABLE","Results are not available until the quiz has finished.");
  return quiz;
 }
 record Totals(long questions,long maximum) {}
 record Aggregate(int number,String first,String last,long answered,long correct,long score) {}
 private Totals totals(UUID quiz) {
  return jdbc.queryForObject("SELECT count(*), coalesce(sum(points::bigint),0) FROM quiz_questions WHERE quiz_session_id=?",(rs,n)->new Totals(rs.getLong(1),rs.getLong(2)),quiz);
 }
 // Include everyone registered by opening, plus late registrants with an accepted answer.
 private static final String AGGREGATES="""
  SELECT p.participant_number, p.first_name, p.last_name,
         count(a.id) AS answered, count(a.id) FILTER (WHERE o.correct) AS correct,
         coalesce(sum(a.awarded_points::bigint),0) AS score
    FROM participants p
    LEFT JOIN quiz_answers a ON a.participant_id=p.id
       AND a.question_id IN (SELECT id FROM quiz_questions WHERE quiz_session_id=?)
    LEFT JOIN quiz_options o ON o.id=a.selected_option_id AND o.question_id=a.question_id
   WHERE (p.created_at <= ? OR EXISTS (SELECT 1 FROM quiz_answers ea JOIN quiz_questions eq ON eq.id=ea.question_id WHERE ea.participant_id=p.id AND eq.quiz_session_id=?))
  """;
 private List<Aggregate> aggregates(QuizSession quiz, UUID participant) {
  String sql=AGGREGATES+(participant==null?"":" AND p.id=?")+" GROUP BY p.id,p.participant_number,p.first_name,p.last_name ORDER BY score DESC,p.participant_number ASC";
  var mapper=(org.springframework.jdbc.core.RowMapper<Aggregate>)(rs,n)->new Aggregate(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getLong(5),rs.getLong(6));
  return participant==null?jdbc.query(sql,mapper,quiz.getId(),Timestamp.from(quiz.getOpenedAt()),quiz.getId()):jdbc.query(sql,mapper,quiz.getId(),Timestamp.from(quiz.getOpenedAt()),quiz.getId(),participant);
 }
 static BigDecimal accuracy(long correct,long questions) { return questions==0?BigDecimal.ZERO.setScale(1):BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(questions),1,RoundingMode.HALF_UP); }
 static List<QuizResultsDtos.Entry> rank(List<Aggregate> rows,long questions) {
  List<QuizResultsDtos.Entry> entries=new ArrayList<>(); long previous=Long.MIN_VALUE, rank=0;
  for(int i=0;i<rows.size();i++) { var r=rows.get(i);if(i==0||r.score()!=previous) rank=i+1;previous=r.score();entries.add(new QuizResultsDtos.Entry(rank,r.number(),r.first(),r.last(),r.score(),r.answered(),r.correct(),r.answered()-r.correct(),questions-r.answered())); }
  return List.copyOf(entries);
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public QuizResultsDtos.Leaderboard leaderboard() {
  var quiz=finished();var totals=totals(quiz.getId());var rows=aggregates(quiz,null);var ranked=rank(rows,totals.questions());
  long highest=rows.isEmpty()?0:rows.getFirst().score();long answered=rows.stream().filter(r->r.answered()>0).count();long first=ranked.stream().filter(r->r.rank()==1).count();
  BigDecimal sum=rows.stream().map(r->BigDecimal.valueOf(r.score())).reduce(BigDecimal.ZERO,BigDecimal::add);
  BigDecimal average=rows.isEmpty()?BigDecimal.ZERO.setScale(1):sum.divide(BigDecimal.valueOf(rows.size()),1,RoundingMode.HALF_UP);
  return new QuizResultsDtos.Leaderboard(quiz.getId(),quiz.getTitle(),quiz.getStatus().name(),totals.questions(),totals.maximum(),rows.size(),answered,highest,average,first,quiz.getCompletedAt(),ranked);
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public QuizResultsDtos.Personal personal(UUID participant) {
  if(participant==null) throw new QuizAdminException(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED","Participant authentication required.");
  var quiz=finished();var totals=totals(quiz.getId());var rows=aggregates(quiz,participant);
  if(rows.isEmpty()) throw new QuizAdminException(HttpStatus.NOT_FOUND,"QUIZ_PARTICIPANT_NOT_ELIGIBLE","Personal results are not available.");
  var row=rows.getFirst();return new QuizResultsDtos.Personal(quiz.getId(),quiz.getTitle(),quiz.getStatus().name(),totals.questions(),row.answered(),row.correct(),row.answered()-row.correct(),totals.questions()-row.answered(),row.score(),totals.maximum(),accuracy(row.correct(),totals.questions()),quiz.getCompletedAt());
 }
}
