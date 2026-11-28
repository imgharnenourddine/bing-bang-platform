package ma.enset.bigbang.quiz;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.enset.bigbang.participant.ParticipantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuizAnswerServiceTest {
 private final ma.enset.bigbang.notification.RealtimeEvents realtime=mock(ma.enset.bigbang.notification.RealtimeEvents.class);
 private final Instant start=Instant.parse("2026-11-28T13:00:00Z");private final UUID person=UUID.randomUUID();
 private final QuizSessionRepository sessions=mock(QuizSessionRepository.class);private final QuizOptionRepository options=mock(QuizOptionRepository.class);private final QuizAnswerRepository answers=mock(QuizAnswerRepository.class);private final QuizQuestionRepository questions=mock(QuizQuestionRepository.class);private final ParticipantRepository participants=mock(ParticipantRepository.class);private final JdbcTemplate jdbc=mock(JdbcTemplate.class);private final EntityManager em=mock(EntityManager.class);
 private final QuizSession quiz=new QuizSession(UUID.randomUUID(),"Quiz",start.minusSeconds(1));private final QuizQuestion question=new QuizQuestion(UUID.randomUUID(),quiz,"Question",1,30,100,start);private final QuizOption correct=new QuizOption(UUID.randomUUID(),question,"Yes",1,true);
 private QuizAnswerService service(Instant now){var service=new QuizAnswerService(sessions,options,answers,questions,participants,jdbc,em,Clock.fixed(now,ZoneOffset.UTC));service.setRealtime(realtime);return service;}
 private void setup(){quiz.start(start);quiz.openQuestion(question,start);when(sessions.findAll()).thenReturn(List.of(quiz));when(sessions.findByIdForAnswer(quiz.getId())).thenReturn(Optional.of(quiz));when(options.findByIdAndQuestionId(correct.getId(),question.getId())).thenReturn(Optional.of(correct));when(participants.existsById(person)).thenReturn(true);}
 private QuizAnswerDtos.Submit input(){return new QuizAnswerDtos.Submit(question.getId(),correct.getId());}
 @Test void deadlineBoundariesRejectAtAndAfterButAllowBefore(){setup();assertEquals("QUESTION_DEADLINE_EXPIRED",assertThrows(QuizAdminException.class,()->service(start.plusSeconds(30)).submit(person,input())).code());assertEquals("QUESTION_DEADLINE_EXPIRED",assertThrows(QuizAdminException.class,()->service(start.plusSeconds(31)).submit(person,input())).code());
  when(jdbc.query(startsWith("INSERT INTO quiz_answers"),org.mockito.ArgumentMatchers.<RowMapper<Instant>>any(),any(),any(),any(),any(),any(),any())).thenReturn(List.of(start.plusSeconds(29)));
  var result=service(start.plusSeconds(30).minusNanos(1)).submit(person,input());assertTrue(result.created());assertEquals(correct.getId(),result.answer().selectedOptionId());verify(realtime).quizAdmin(eq(ma.enset.bigbang.notification.EventType.QUIZ_ANSWER_COUNT_UPDATED),any());}
 @Test void rejectsWrongQuestionInvalidOptionAndInactiveQuiz(){setup();assertEquals("QUESTION_MISMATCH",assertThrows(QuizAdminException.class,()->service(start).submit(person,new QuizAnswerDtos.Submit(UUID.randomUUID(),correct.getId()))).code());assertEquals("INVALID_ANSWER_OPTION",assertThrows(QuizAdminException.class,()->service(start).submit(person,new QuizAnswerDtos.Submit(question.getId(),UUID.randomUUID()))).code());quiz.closeQuestion(start.plusSeconds(2));assertEquals("QUESTION_NOT_ACTIVE",assertThrows(QuizAdminException.class,()->service(start.plusSeconds(2)).submit(person,input())).code());}
 @Test void rejectsMissingIdentity(){assertEquals("AUTHENTICATION_REQUIRED",assertThrows(QuizAdminException.class,()->service(start).submit(null,input())).code());}
 @Test void feedbackUnavailableBeforeClosedAndStatisticsAreAdminData(){setup();when(jdbc.queryForObject(startsWith("SELECT EXISTS"),eq(Boolean.class),eq(quiz.getId()),eq(question.getId()))).thenReturn(false);assertEquals("ANSWER_FEEDBACK_UNAVAILABLE",assertThrows(QuizAdminException.class,()->service(start).feedback(person,question.getId())).code());when(questions.findByIdAndQuizSessionId(question.getId(),quiz.getId())).thenReturn(Optional.of(question));when(answers.countByQuestionId(question.getId())).thenReturn(2L);when(participants.count()).thenReturn(10L);assertEquals(10,service(start).statistics(question.getId()).registeredParticipants());}
 @Test void acknowledgmentDoesNotIncludeScoresOrCorrectness(){assertEquals(List.of("questionId","selectedOptionId","status","submittedAt"),java.util.Arrays.stream(QuizAnswerDtos.Acknowledgment.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());}
 @Test void recoveryAfterClosureDoesNotInsertAndDifferentOptionConflicts(){setup();quiz.closeQuestion(start.plusSeconds(2));
  var old=new QuizAnswerDtos.Acknowledgment(question.getId(),correct.getId(),"ACCEPTED",start.plusSeconds(1));
  when(jdbc.query(startsWith("SELECT a.selected_option_id"),org.mockito.ArgumentMatchers.<RowMapper<QuizAnswerDtos.Acknowledgment>>any(),any(),any(),any())).thenReturn(List.of(old));
  assertFalse(service(start.plusSeconds(90)).submit(person,input()).created());
  assertEquals("ANSWER_ALREADY_SUBMITTED",assertThrows(QuizAdminException.class,()->service(start.plusSeconds(90)).submit(person,new QuizAnswerDtos.Submit(question.getId(),UUID.randomUUID()))).code());
  verifyNoInteractions(realtime);verify(jdbc,never()).query(startsWith("INSERT INTO quiz_answers"),org.mockito.ArgumentMatchers.<RowMapper<Instant>>any(),any(),any(),any(),any(),any(),any());
 }
 @Test void unansweredClosedQuestionHasNullCorrectnessAndZeroPoints(){setup();
  when(jdbc.queryForObject(startsWith("SELECT EXISTS"),eq(Boolean.class),eq(quiz.getId()),eq(question.getId()))).thenReturn(true);
  when(questions.findByIdAndQuizSessionId(question.getId(),quiz.getId())).thenReturn(Optional.of(question));
  when(options.findByQuestionIdAndCorrectTrue(question.getId())).thenReturn(Optional.of(correct));
  var feedback=service(start.plusSeconds(50)).feedback(person,question.getId());
  assertFalse(feedback.hasAnswered());assertNull(feedback.correct());assertNull(feedback.selectedOptionId());assertEquals(0,feedback.awardedPoints());
 }
}
