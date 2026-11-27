package ma.enset.bigbang.draw;

import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DrawController.class)
public class DrawErrorHandler {
    @ExceptionHandler(DrawException.class)
    ProblemDetail drawError(DrawException exception) { return problem(exception.status(), exception.code(), exception.getMessage()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail constraintConflict(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT, "DRAW_CONFLICT", "Draw state changed concurrently");
    }
    private ProblemDetail problem(HttpStatus status, String code, String message) {
        var problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setProperty("code", code); problem.setProperty("timestamp", Instant.now()); return problem;
    }
}
