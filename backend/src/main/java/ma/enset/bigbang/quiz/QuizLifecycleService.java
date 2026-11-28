package ma.enset.bigbang.quiz;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizLifecycleService {
 private final QuizSessionRepository sessions;private final QuizQuestionRepository questions;private final QuizOptionRepository options;private final QuizAdminService preparation;private final JdbcTemplate jdbc;private final Clock clock;private final EntityManager em;
 public QuizLifecycleService(QuizSessionRepository sessions,QuizQuestionRepository questions,QuizOptionRepository options,QuizAdminService preparation,JdbcTemplate jdbc,Clock clock,EntityManager em){this.sessions=sessions;this.questions=questions;this.options=options;this.preparation=preparation;this.jdbc=jdbc;this.clock=clock;this.em=em;}
 private QuizAdminException conflict(String code,String message){return new QuizAdminException(HttpStatus.CONFLICT,code,message);}
 private QuizSession session(){var all=sessions.findAll();if(all.isEmpty())throw new QuizAdminException(HttpStatus.NOT_FOUND,"QUIZ_NOT_FOUND","Quiz not found.");if(all.size()!=1)throw conflict("AMBIGUOUS_QUIZ","Multiple quiz sessions require reconciliation.");return all.getFirst();}
 private QuizSession locked(long version){var q=sessions.findByIdForUpdate(session().getId()).orElseThrow(()->new QuizAdminException(HttpStatus.NOT_FOUND,"QUIZ_NOT_FOUND","Quiz not found."));em.refresh(q,LockModeType.PESSIMISTIC_WRITE);if(q.getVersion()!=version)throw conflict("STALE_QUIZ_STATE","Quiz state changed; reload and try again.");return q;}
 private void require(QuizSession q,QuizStatus state){if(q.getStatus()!=state)throw conflict("INVALID_QUIZ_STATE","Operation is not valid in the current quiz state.");}
 private void expected(QuizSession q,UUID id){if(q.getCurrentQuestion()==null||!q.getCurrentQuestion().getId().equals(id))throw conflict("STALE_QUIZ_STATE","Current question changed; reload and try again.");}
 private QuizQuestion next(QuizSession q){return questions.findFirstByQuizSessionIdAndPositionGreaterThanOrderByPositionAsc(q.getId(),q.getCurrentQuestion()==null?0:q.getCurrentQuestion().getPosition()).orElse(null);}
 private void open(QuizSession q,QuizQuestion item,Instant now){q.openQuestion(item,now);jdbc.update("INSERT INTO quiz_question_timings(question_id,quiz_session_id,opened_at,deadline) VALUES (?,?,?,?)",item.getId(),q.getId(),java.sql.Timestamp.from(now),java.sql.Timestamp.from(q.getQuestionDeadline()));sessions.saveAndFlush(q);}
 private void close(QuizSession q,Instant now){q.closeQuestion(now);jdbc.update("INSERT INTO quiz_question_timings(question_id,quiz_session_id,opened_at,deadline,closed_at) VALUES (?,?,?,?,?) ON CONFLICT (question_id) DO UPDATE SET closed_at=EXCLUDED.closed_at WHERE quiz_question_timings.closed_at IS NULL",q.getCurrentQuestion().getId(),q.getId(),java.sql.Timestamp.from(q.getQuestionOpenedAt()),java.sql.Timestamp.from(q.getQuestionDeadline()),java.sql.Timestamp.from(q.getQuestionClosedAt()));sessions.saveAndFlush(q);}
 private QuizLifecycleDtos.AdminState view(QuizSession q){var current=q.getCurrentQuestion();boolean hasNext=next(q)!=null;var actions=switch(q.getStatus()){case DRAFT->List.of("START");case OPEN->List.of("OPEN_FIRST");case QUESTION_ACTIVE->List.of("CLOSE");case QUESTION_CLOSED->hasNext?List.of("NEXT"):List.of("FINISH");case FINISHED->List.<String>of();};return new QuizLifecycleDtos.AdminState(q.getId(),q.getTitle(),q.getStatus(),current==null?null:current.getId(),current==null?null:current.getPosition(),questions.countByQuizSessionId(q.getId()),q.getQuestionOpenedAt(),q.getQuestionDeadline(),q.getQuestionClosedAt(),q.getOpenedAt(),q.getCompletedAt(),clock.instant(),hasNext,actions,q.getVersion());}
 @Transactional public QuizLifecycleDtos.AdminState start(long version){var q=locked(version);require(q,QuizStatus.DRAFT);if(!preparation.readiness().ready())throw conflict("QUIZ_NOT_READY","Quiz preparation is incomplete.");q.start(clock.instant());sessions.saveAndFlush(q);return view(q);}
 @Transactional public QuizLifecycleDtos.AdminState openFirst(long version){var q=locked(version);require(q,QuizStatus.OPEN);var item=next(q);if(item==null)throw conflict("NO_NEXT_QUESTION","No question is available.");open(q,item,clock.instant());return view(q);}
 @Transactional public QuizLifecycleDtos.AdminState closeCurrent(UUID id,long version){var q=locked(version);require(q,QuizStatus.QUESTION_ACTIVE);expected(q,id);close(q,clock.instant());return view(q);}
 @Transactional public QuizLifecycleDtos.AdminState openNext(UUID id,long version){var q=locked(version);require(q,QuizStatus.QUESTION_CLOSED);expected(q,id);var item=next(q);if(item==null)throw conflict("NO_NEXT_QUESTION","No further question is available.");open(q,item,clock.instant());return view(q);}
 @Transactional public QuizLifecycleDtos.AdminState finish(UUID id,long version){var q=locked(version);require(q,QuizStatus.QUESTION_CLOSED);expected(q,id);if(next(q)!=null)throw conflict("QUIZ_NOT_COMPLETABLE","Questions remain.");q.finish(clock.instant());sessions.saveAndFlush(q);return view(q);}
 @Transactional(readOnly=true) public QuizLifecycleDtos.AdminState status(){return view(session());}
 @Transactional(readOnly=true) public QuizLifecycleDtos.ParticipantState participant(UUID participantId){var q=session();var current=q.getCurrentQuestion();boolean visible=q.getStatus()==QuizStatus.QUESTION_ACTIVE;Instant now=clock.instant();var list=visible?options.findByQuestionIdOrderByPositionAsc(current.getId()).stream().map(o->new QuizLifecycleDtos.PublicOption(o.getId(),o.getOptionText(),o.getPosition())).toList():List.<QuizLifecycleDtos.PublicOption>of();var chosen=visible?jdbc.query("SELECT selected_option_id FROM quiz_answers WHERE question_id=? AND participant_id=?",(rs,i)->rs.getObject(1,UUID.class),current.getId(),participantId):List.<UUID>of();UUID selected=chosen.isEmpty()?null:chosen.getFirst();return new QuizLifecycleDtos.ParticipantState(q.getTitle(),q.getStatus(),visible?current.getId():null,visible?current.getPosition():null,questions.countByQuizSessionId(q.getId()),visible?current.getQuestionText():null,list,visible?q.getQuestionOpenedAt():null,visible?q.getQuestionDeadline():null,now,selected!=null,selected,visible&&selected==null&&now.isBefore(q.getQuestionDeadline()));}
 @Transactional public void closeExpired(UUID sessionId,UUID questionId){var q=sessions.findByIdForUpdate(sessionId).orElse(null);if(q==null||q.getStatus()!=QuizStatus.QUESTION_ACTIVE||q.getCurrentQuestion()==null||!q.getCurrentQuestion().getId().equals(questionId)||clock.instant().isBefore(q.getQuestionDeadline()))return;close(q,clock.instant());}
}
