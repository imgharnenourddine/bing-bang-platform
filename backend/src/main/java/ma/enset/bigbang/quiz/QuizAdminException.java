package ma.enset.bigbang.quiz;

import org.springframework.http.HttpStatus;
public class QuizAdminException extends RuntimeException {
    private final HttpStatus status; private final String code;
    public QuizAdminException(HttpStatus status,String code,String detail) { super(detail);this.status=status;this.code=code; }
    public HttpStatus status(){return status;} public String code(){return code;}
}
