package ma.enset.bigbang.quiz;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuizAdminServiceTest {
    QuizSessionRepository sessions=mock(QuizSessionRepository.class); QuizQuestionRepository questions=mock(QuizQuestionRepository.class);
    QuizOptionRepository options=mock(QuizOptionRepository.class); JdbcTemplate jdbc=mock(JdbcTemplate.class); EntityManager em=mock(EntityManager.class);
    QuizAdminService service=new QuizAdminService(sessions,questions,options,jdbc,em);
    QuizSession quiz=new QuizSession(UUID.randomUUID(),"Quiz",Instant.now());
    @BeforeEach void setup(){when(sessions.findAll()).thenReturn(List.of(quiz));when(sessions.findByIdForUpdate(quiz.getId())).thenReturn(Optional.of(quiz));}
    QuizAdminDtos.QuestionInput input(List<QuizAdminDtos.OptionInput> opts){return new QuizAdminDtos.QuestionInput("Question",30,100,opts,0L);}
    @Test void rejectsMissingMultipleAndDuplicateCorrectOptions(){
        for(var opts:List.of(List.of(new QuizAdminDtos.OptionInput("Only",true)),List.of(new QuizAdminDtos.OptionInput("A",false),new QuizAdminDtos.OptionInput("B",false)),List.of(new QuizAdminDtos.OptionInput("A",true),new QuizAdminDtos.OptionInput("B",true)),List.of(new QuizAdminDtos.OptionInput(" A ",true),new QuizAdminDtos.OptionInput("a",false)))){
            assertEquals(HttpStatus.BAD_REQUEST,assertThrows(QuizAdminException.class,()->service.createQuestion(input(opts))).status());
        }
        verifyNoInteractions(jdbc);
    }
    @Test void rejectsStaleVersionsAndIncompleteReordering(){
        assertEquals("STALE_QUIZ_VERSION",assertThrows(QuizAdminException.class,()->service.reorder(new QuizAdminDtos.Reorder(List.of(),1L))).code());
        var q=new QuizQuestion(UUID.randomUUID(),quiz,"Q",1,30,100,Instant.now());
        when(questions.findByQuizSessionIdOrderByPositionAsc(quiz.getId())).thenReturn(List.of(q));
        assertEquals("INVALID_ORDER",assertThrows(QuizAdminException.class,()->service.reorder(new QuizAdminDtos.Reorder(List.of(),0L))).code());
        assertEquals("INVALID_ORDER",assertThrows(QuizAdminException.class,()->service.reorder(new QuizAdminDtos.Reorder(List.of(q.getId(),q.getId()),0L))).code());
    }
    @Test void invalidQuestionAndExcessiveOptionsAreRejected() {
        var options=java.util.stream.IntStream.range(0,11).mapToObj(i->new QuizAdminDtos.OptionInput("Option "+i,i==0)).toList();
        assertEquals("INVALID_OPTIONS",assertThrows(QuizAdminException.class,()->service.createQuestion(input(options))).code());
        assertEquals("INVALID_QUESTION",assertThrows(QuizAdminException.class,()->service.createQuestion(new QuizAdminDtos.QuestionInput(" ",0,0,List.of(),0L))).code());
    }
    @Test void missingQuestionIsScopedToQuiz() {
        UUID id=UUID.randomUUID();when(questions.findByIdAndQuizSessionId(id,quiz.getId())).thenReturn(Optional.empty());
        assertEquals("QUESTION_NOT_FOUND",assertThrows(QuizAdminException.class,()->service.getQuestion(id)).code());
    }
    @Test void nonDraftSessionRejectsMutations() throws Exception {
        var field=QuizSession.class.getDeclaredField("status");field.setAccessible(true);
        for(var status:List.of(QuizStatus.OPEN,QuizStatus.QUESTION_ACTIVE,QuizStatus.QUESTION_CLOSED,QuizStatus.FINISHED)){
            field.set(quiz,status);
            assertEquals("QUIZ_NOT_DRAFT",assertThrows(QuizAdminException.class,()->service.reorder(new QuizAdminDtos.Reorder(List.of(),0L))).code());
        }
    }
    @Test void duplicateCreationAndEmptyReadinessAreReported(){
        assertEquals("QUIZ_EXISTS",assertThrows(QuizAdminException.class,()->service.create(new QuizAdminDtos.CreateQuiz("Again"))).code());
        when(questions.findByQuizSessionIdOrderByPositionAsc(quiz.getId())).thenReturn(List.of());
        assertFalse(service.readiness().ready());
    }
    @Test void validConfigurationUpdateEmitsAdminInvalidation(){
        var events=mock(ma.enset.bigbang.notification.RealtimeEvents.class);service.setRealtime(events);
        service.update(new QuizAdminDtos.UpdateQuiz("Updated",0L));
        verify(events).quizAdmin(eq(ma.enset.bigbang.notification.EventType.QUIZ_CONFIGURATION_UPDATED),any());
        assertThrows(QuizAdminException.class,()->service.update(new QuizAdminDtos.UpdateQuiz("Other",1L)));
        verifyNoMoreInteractions(events);
    }
}
