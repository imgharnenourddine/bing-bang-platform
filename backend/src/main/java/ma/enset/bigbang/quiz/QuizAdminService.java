package ma.enset.bigbang.quiz;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizAdminService {
    private final QuizSessionRepository sessions; private final QuizQuestionRepository questions; private final QuizOptionRepository options;
    private final JdbcTemplate jdbc; private final EntityManager em;
    public QuizAdminService(QuizSessionRepository sessions,QuizQuestionRepository questions,QuizOptionRepository options,JdbcTemplate jdbc,EntityManager em) {
        this.sessions=sessions;this.questions=questions;this.options=options;this.jdbc=jdbc;this.em=em;
    }
    private QuizAdminException error(HttpStatus s,String code,String msg){return new QuizAdminException(s,code,msg);}
    private QuizSession onlySession(){var all=sessions.findAll();if(all.isEmpty())throw error(HttpStatus.NOT_FOUND,"QUIZ_NOT_FOUND","Quiz has not been created.");if(all.size()!=1)throw error(HttpStatus.CONFLICT,"AMBIGUOUS_QUIZ","Multiple quiz sessions require administrative reconciliation.");return all.get(0);}
    private QuizSession locked(Long version){var q=sessions.findByIdForUpdate(onlySession().getId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"QUIZ_NOT_FOUND","Quiz not found."));
        if(q.getStatus()!=QuizStatus.DRAFT)throw error(HttpStatus.CONFLICT,"QUIZ_NOT_DRAFT","Quiz preparation is closed.");
        if(version==null||q.getVersion()!=version)throw error(HttpStatus.CONFLICT,"STALE_QUIZ_VERSION","Quiz was changed; reload before editing.");return q;}
    private void touch(QuizSession q){q.touch(Instant.now());sessions.saveAndFlush(q);}
    private QuizAdminDtos.Quiz view(QuizSession q){return new QuizAdminDtos.Quiz(q.getId(),q.getTitle(),q.getStatus(),q.getVersion(),questions.countByQuizSessionId(q.getId()),q.getCreatedAt(),q.getUpdatedAt());}
    private QuizQuestion question(QuizSession quiz,UUID id){return questions.findByIdAndQuizSessionId(id,quiz.getId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"QUESTION_NOT_FOUND","Question not found."));}
    private QuizAdminDtos.Question questionView(QuizQuestion q,List<QuizOption> values,long version){return new QuizAdminDtos.Question(q.getId(),q.getQuestionText(),q.getPosition(),q.getDurationSeconds(),q.getPoints(),values.stream().map(o->new QuizAdminDtos.Option(o.getId(),o.getOptionText(),o.getPosition(),o.isCorrect())).toList(),q.getCreatedAt(),q.getUpdatedAt(),version);}
    private void validOptions(List<QuizAdminDtos.OptionInput> values){
        if(values==null||values.size()<2||values.size()>10||values.stream().anyMatch(Objects::isNull))throw error(HttpStatus.BAD_REQUEST,"INVALID_OPTIONS","Provide 2 to 10 options.");
        var names=new HashSet<String>();int correct=0;
        for(var o:values){if(o.text()==null||o.text().isBlank()||o.text().strip().length()>1000||o.correct()==null)throw error(HttpStatus.BAD_REQUEST,"INVALID_OPTIONS","Every option must have text and correctness.");
            if(!names.add(o.text().strip().toLowerCase(Locale.ROOT)))throw error(HttpStatus.BAD_REQUEST,"DUPLICATE_OPTIONS","Option text must be unique ignoring case and surrounding whitespace.");if(o.correct())correct++;}
        if(correct!=1)throw error(HttpStatus.BAD_REQUEST,"INVALID_CORRECT_OPTIONS","Exactly one option must be correct.");
    }
    private void validQuestion(QuizAdminDtos.QuestionInput input){if(input==null||input.text()==null||input.text().isBlank()||input.text().strip().length()>2000||(input.durationSeconds()!=null&&input.durationSeconds()<=0)||(input.points()!=null&&input.points()<=0))throw error(HttpStatus.BAD_REQUEST,"INVALID_QUESTION","Question, duration or points are invalid.");validOptions(input.options());}
    private void writeOptions(QuizQuestion q,List<QuizAdminDtos.OptionInput> values){int position=1;for(var o:values)options.save(new QuizOption(UUID.randomUUID(),q,o.text(),position++,o.correct()));options.flush();}
    @Transactional public QuizAdminDtos.Quiz create(QuizAdminDtos.CreateQuiz input){
        if(input==null||input.title()==null||input.title().isBlank()||input.title().strip().length()>200)throw error(HttpStatus.BAD_REQUEST,"INVALID_QUIZ","A title of at most 200 characters is required.");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", 612047221L);if(!sessions.findAll().isEmpty())throw error(HttpStatus.CONFLICT,"QUIZ_EXISTS","An event quiz already exists.");
        return view(sessions.saveAndFlush(new QuizSession(UUID.randomUUID(),input.title(),Instant.now())));
    }
    @Transactional(readOnly=true) public QuizAdminDtos.Quiz get(){return view(onlySession());}
    @Transactional public QuizAdminDtos.Quiz update(QuizAdminDtos.UpdateQuiz input){var q=locked(input.expectedVersion());q.rename(input.title(),Instant.now());touch(q);return view(q);}
    @Transactional(readOnly=true) public List<QuizAdminDtos.Question> list(){var q=onlySession();var byQuestion=options.findByQuestionQuizSessionIdOrderByQuestionPositionAscPositionAsc(q.getId()).stream().collect(Collectors.groupingBy(o->o.getQuestion().getId()));return questions.findByQuizSessionIdOrderByPositionAsc(q.getId()).stream().map(item->questionView(item,byQuestion.getOrDefault(item.getId(),List.of()),q.getVersion())).toList();}
    @Transactional(readOnly=true) public QuizAdminDtos.Question getQuestion(UUID id){var q=onlySession();var item=question(q,id);return questionView(item,options.findByQuestionIdOrderByPositionAsc(id),q.getVersion());}
    @Transactional public QuizAdminDtos.Question createQuestion(QuizAdminDtos.QuestionInput input){validQuestion(input);var q=locked(input.expectedVersion());var existing=questions.findByQuizSessionIdOrderByPositionAsc(q.getId());int position=existing.isEmpty()?1:Math.addExact(existing.getLast().getPosition(),1);var item=questions.saveAndFlush(new QuizQuestion(UUID.randomUUID(),q,input.text(),position,input.durationSeconds()==null?30:input.durationSeconds(),input.points()==null?100:input.points(),Instant.now()));writeOptions(item,input.options());touch(q);return questionView(item,options.findByQuestionIdOrderByPositionAsc(item.getId()),q.getVersion());}
    @Transactional public QuizAdminDtos.Question updateQuestion(UUID id,QuizAdminDtos.QuestionInput input){validQuestion(input);var q=locked(input.expectedVersion());var item=question(q,id);item.revise(input.text(),input.durationSeconds()==null?30:input.durationSeconds(),input.points()==null?100:input.points(),Instant.now());
        jdbc.update("DELETE FROM quiz_options WHERE question_id=?",id);writeOptions(item,input.options());touch(q);return questionView(item,options.findByQuestionIdOrderByPositionAsc(id),q.getVersion());}
    @Transactional public void deleteQuestion(UUID id,long version){var q=locked(version);question(q,id);jdbc.update("DELETE FROM quiz_options WHERE question_id=?",id);jdbc.update("DELETE FROM quiz_questions WHERE id=? AND quiz_session_id=?",id,q.getId());var remaining=questions.findByQuizSessionIdOrderByPositionAsc(q.getId());reposition(q,remaining.stream().map(QuizQuestion::getId).toList());}
    private void reposition(QuizSession q,List<UUID> ids){var positions=questions.findByQuizSessionIdOrderByPositionAsc(q.getId());long max=positions.stream().mapToLong(QuizQuestion::getPosition).max().orElse(0);if(max+ids.size()>Integer.MAX_VALUE)throw error(HttpStatus.CONFLICT,"POSITION_OVERFLOW","Question positions cannot be reordered.");
        int i=1;for(UUID id:ids)jdbc.update("UPDATE quiz_questions SET position=? WHERE id=? AND quiz_session_id=?",(int)max+i++,id,q.getId());i=1;for(UUID id:ids)jdbc.update("UPDATE quiz_questions SET position=?,updated_at=? WHERE id=? AND quiz_session_id=?",i++,java.sql.Timestamp.from(Instant.now()),id,q.getId());touch(q);em.clear();}
    @Transactional public void reorder(QuizAdminDtos.Reorder input){var q=locked(input.expectedVersion());var existing=questions.findByQuizSessionIdOrderByPositionAsc(q.getId()).stream().map(QuizQuestion::getId).toList();var ids=input.questionIds();if(ids==null||ids.size()!=existing.size()||ids.stream().anyMatch(Objects::isNull)||new HashSet<>(ids).size()!=ids.size()||!new HashSet<>(ids).equals(new HashSet<>(existing)))throw error(HttpStatus.BAD_REQUEST,"INVALID_ORDER","Provide each question ID exactly once.");reposition(q,ids);}
    @Transactional(readOnly=true) public QuizAdminDtos.Readiness readiness(){List<QuizAdminDtos.Question> items;QuizSession q;try{q=onlySession();}catch(QuizAdminException e){if(e.status()==HttpStatus.NOT_FOUND)return new QuizAdminDtos.Readiness(false,0,List.of("Quiz configuration is missing."));throw e;}items=list();var issues=new ArrayList<String>();if(q.getStatus()!=QuizStatus.DRAFT)issues.add("Quiz is not in DRAFT state.");if(items.isEmpty())issues.add("At least one question is required.");int expected=1;for(var item:items){if(item.position()!=expected++)issues.add("Question positions must be contiguous.");if(item.text()==null||item.text().isBlank()||item.durationSeconds()<=0||item.points()<=0)issues.add("A question has invalid text, duration or points.");if(item.options().size()<2||item.options().size()>10||item.options().stream().filter(QuizAdminDtos.Option::correct).count()!=1)issues.add("Each question requires 2–10 options and exactly one correct option.");var optionNames=new HashSet<String>();int optionPosition=1;for(var o:item.options()){if(o.position()!=optionPosition++||o.text()==null||o.text().isBlank()||!optionNames.add(o.text().strip().toLowerCase(Locale.ROOT)))issues.add("Options require unique text and contiguous positions.");}}return new QuizAdminDtos.Readiness(issues.isEmpty(),items.size(),List.copyOf(issues));}
}
