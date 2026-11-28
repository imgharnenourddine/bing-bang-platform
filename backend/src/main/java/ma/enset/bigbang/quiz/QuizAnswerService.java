package ma.enset.bigbang.quiz;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import ma.enset.bigbang.participant.ParticipantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class QuizAnswerService {
 private final QuizSessionRepository sessions;private final QuizOptionRepository options;private final QuizAnswerRepository answers;private final QuizQuestionRepository questions;private final ParticipantRepository participants;private final JdbcTemplate jdbc;private final EntityManager em;private final Clock clock;
 public QuizAnswerService(QuizSessionRepository sessions,QuizOptionRepository options,QuizAnswerRepository answers,QuizQuestionRepository questions,ParticipantRepository participants,JdbcTemplate jdbc,EntityManager em,Clock clock){this.sessions=sessions;this.options=options;this.answers=answers;this.questions=questions;this.participants=participants;this.jdbc=jdbc;this.em=em;this.clock=clock;}
 public record Submission(QuizAnswerDtos.Acknowledgment answer,boolean created) { }
 private QuizAdminException error(HttpStatus status,String code,String message){return new QuizAdminException(status,code,message);}
 private QuizSession session(){var all=sessions.findAll();if(all.isEmpty())throw error(HttpStatus.NOT_FOUND,"QUIZ_NOT_FOUND","Quiz not found.");if(all.size()!=1)throw error(HttpStatus.CONFLICT,"AMBIGUOUS_QUIZ","Quiz state unavailable.");return all.getFirst();}
 private QuizSession shared(){var s=sessions.findByIdForAnswer(session().getId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"QUIZ_NOT_FOUND","Quiz not found."));em.refresh(s,LockModeType.PESSIMISTIC_READ);return s;}
 private QuizAnswerDtos.Acknowledgment ack(UUID question,UUID option,Instant time){return new QuizAnswerDtos.Acknowledgment(question,option,"ACCEPTED",time);}
 private List<QuizAnswerDtos.Acknowledgment> existing(UUID session,UUID question,UUID participant){return jdbc.query("SELECT a.selected_option_id,a.submitted_at FROM quiz_answers a JOIN quiz_questions q ON q.id=a.question_id WHERE q.quiz_session_id=? AND a.question_id=? AND a.participant_id=?",(rs,i)->ack(question,rs.getObject(1,UUID.class),rs.getTimestamp(2).toInstant()),session,question,participant);}
 private Submission recover(QuizAnswerDtos.Acknowledgment old,UUID option){if(!old.selectedOptionId().equals(option))throw error(HttpStatus.CONFLICT,"ANSWER_ALREADY_SUBMITTED","An answer has already been accepted for this question.");return new Submission(old,false);}
 @Transactional(isolation=Isolation.READ_COMMITTED) public Submission submit(UUID participant,QuizAnswerDtos.Submit input){
  if(participant==null)throw error(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED","Participant authentication required.");
  if(input==null||input.questionId()==null||input.optionId()==null)throw error(HttpStatus.BAD_REQUEST,"INVALID_ANSWER","Question and option are required.");
  var q=shared();var prior=existing(q.getId(),input.questionId(),participant);if(!prior.isEmpty())return recover(prior.getFirst(),input.optionId());
  if(q.getStatus()!=QuizStatus.QUESTION_ACTIVE||q.getCurrentQuestion()==null)throw error(HttpStatus.CONFLICT,"QUESTION_NOT_ACTIVE","No question is accepting answers.");
  if(!q.getCurrentQuestion().getId().equals(input.questionId()))throw error(HttpStatus.CONFLICT,"QUESTION_MISMATCH","This question is not currently active.");
  Instant acceptedAt=clock.instant();if(!acceptedAt.isBefore(q.getQuestionDeadline()))throw error(HttpStatus.CONFLICT,"QUESTION_DEADLINE_EXPIRED","The answer deadline has passed.");
  var option=options.findByIdAndQuestionId(input.optionId(),input.questionId()).orElseThrow(()->error(HttpStatus.BAD_REQUEST,"INVALID_ANSWER_OPTION","Invalid answer option."));
  if(!participants.existsById(participant))throw error(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED","Participant authentication required.");
  int points=option.isCorrect()?q.getCurrentQuestion().getPoints():0;
  var inserted=jdbc.query("INSERT INTO quiz_answers(id,question_id,participant_id,selected_option_id,submitted_at,awarded_points) VALUES (?,?,?,?,?,?) ON CONFLICT (question_id,participant_id) DO NOTHING RETURNING submitted_at",(rs,i)->rs.getTimestamp(1).toInstant(),UUID.randomUUID(),input.questionId(),participant,input.optionId(),Timestamp.from(acceptedAt.truncatedTo(ChronoUnit.MICROS)),points);
  if(!inserted.isEmpty())return new Submission(ack(input.questionId(),input.optionId(),inserted.getFirst()),true);
  prior=existing(q.getId(),input.questionId(),participant);if(prior.isEmpty())throw error(HttpStatus.CONFLICT,"ANSWER_CONFLICT","Answer state changed; reload and try again.");return recover(prior.getFirst(),input.optionId());
 }
 @Transactional(readOnly=true) public QuizAnswerDtos.Acknowledgment answer(UUID participant,UUID question){var q=session();return existing(q.getId(),question,participant).stream().findFirst().orElseThrow(()->error(HttpStatus.NOT_FOUND,"ANSWER_NOT_FOUND","No accepted answer found."));}
 @Transactional(readOnly=true) public QuizAnswerDtos.Feedback feedback(UUID participant,UUID question){
  var q=session();var closed=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM quiz_question_timings WHERE quiz_session_id=? AND question_id=? AND closed_at IS NOT NULL)",Boolean.class,q.getId(),question);
  if(!Boolean.TRUE.equals(closed))throw error(HttpStatus.NOT_FOUND,"ANSWER_FEEDBACK_UNAVAILABLE","Feedback is not available.");
  var item=questions.findByIdAndQuizSessionId(question,q.getId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"QUESTION_NOT_FOUND","Question not found."));
  var correct=options.findByQuestionIdAndCorrectTrue(question).orElseThrow(()->error(HttpStatus.CONFLICT,"ANSWER_FEEDBACK_UNAVAILABLE","Feedback is not available."));
  var answer=answers.findByQuestionIdAndParticipantId(question,participant);return answer.map(a->new QuizAnswerDtos.Feedback(question,a.getSelectedOption().getId(),correct.getId(),true,a.getSelectedOption().getId().equals(correct.getId()),a.getAwardedPoints(),item.getPoints())).orElseGet(()->new QuizAnswerDtos.Feedback(question,null,correct.getId(),false,null,0,item.getPoints()));
 }
 @Transactional(readOnly=true) public QuizAnswerDtos.Statistics statistics(UUID question){var q=session();if(questions.findByIdAndQuizSessionId(question,q.getId()).isEmpty())throw error(HttpStatus.NOT_FOUND,"QUESTION_NOT_FOUND","Question not found.");return new QuizAnswerDtos.Statistics(question,answers.countByQuestionId(question),participants.count());}
}
