package ma.enset.bigbang.draw;
import org.springframework.http.HttpStatus;
public class DrawException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public DrawException(HttpStatus status, String code, String message) { super(message); this.status = status; this.code = code; }
    public HttpStatus status() { return status; }
    public String code() { return code; }
}
