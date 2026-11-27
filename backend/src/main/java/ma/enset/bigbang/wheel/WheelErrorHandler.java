package ma.enset.bigbang.wheel;

import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes=WheelController.class)
public class WheelErrorHandler {
 @ExceptionHandler(WheelException.class) ProblemDetail error(WheelException ex) { return problem(ex.status(),ex.code(),ex.getMessage()); }
 @ExceptionHandler(DataIntegrityViolationException.class) ProblemDetail conflict(DataIntegrityViolationException ex) { return problem(HttpStatus.CONFLICT,"WHEEL_CONFLICT","Wheel state changed concurrently"); }
 private ProblemDetail problem(HttpStatus status,String code,String text) { var p=ProblemDetail.forStatusAndDetail(status,text);p.setProperty("code",code);p.setProperty("timestamp",Instant.now());return p; }
}
