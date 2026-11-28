package ma.enset.bigbang.quiz;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {QuizAdminController.class, QuizLifecycleController.class})
public class QuizAdminErrorHandler {
    @ExceptionHandler(QuizAdminException.class) ProblemDetail domain(QuizAdminException e) { var p=ProblemDetail.forStatusAndDetail(e.status(),e.getMessage());p.setProperty("code",e.code());return p; }
    @ExceptionHandler({DataIntegrityViolationException.class,CannotAcquireLockException.class,org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    ProblemDetail conflict(Exception e) { var p=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Quiz data changed or conflicts with existing records.");p.setProperty("code","QUIZ_CONFLICT");return p; }
}
