package ma.enset.bigbang.quiz;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuizResultsServiceTest {
 @Test void competitionRanksAndTiesUseInputOrder() {
  var rows=List.of(new QuizResultsService.Aggregate(1,"a","a",2,2,100),new QuizResultsService.Aggregate(2,"b","b",1,1,90),new QuizResultsService.Aggregate(3,"c","c",1,1,90),new QuizResultsService.Aggregate(4,"d","d",0,0,0));
  var result=QuizResultsService.rank(rows,3);
  assertEquals(List.of(1L,2L,2L,4L),result.stream().map(QuizResultsDtos.Entry::rank).toList());
  assertEquals(3,result.get(3).unansweredQuestions());
  assertEquals(1,result.getFirst().unansweredQuestions());
 }
 @Test void scoresWithAllTiesShareFirstPlace(){var rows=List.of(new QuizResultsService.Aggregate(1,"a","b",0,0,0),new QuizResultsService.Aggregate(2,"c","d",0,0,0));assertEquals(List.of(1L,1L),QuizResultsService.rank(rows,2).stream().map(QuizResultsDtos.Entry::rank).toList());}
 @Test void accuracyIncludesUnansweredAndRoundsHalfUp(){assertEquals(new BigDecimal("66.7"),QuizResultsService.accuracy(2,3));assertEquals(new BigDecimal("0.0"),QuizResultsService.accuracy(0,0));}
 @Test void unfinishedQuizReturnsConflict(){var repo=mock(QuizSessionRepository.class);var session=new QuizSession(UUID.randomUUID(),"Quiz",Instant.now());when(repo.findAll()).thenReturn(List.of(session));var service=new QuizResultsService(repo,mock(JdbcTemplate.class));assertEquals("QUIZ_RESULTS_NOT_AVAILABLE",assertThrows(QuizAdminException.class,service::leaderboard).code());}
 @Test void personalDtoNeverContainsRankOrOtherResults(){var names=java.util.Arrays.stream(QuizResultsDtos.Personal.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();assertFalse(names.contains("rank"));assertFalse(names.contains("leaderboard"));assertFalse(names.contains("highestScore"));assertFalse(names.contains("participantNumber"));}
}
