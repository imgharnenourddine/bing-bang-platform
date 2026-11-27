package ma.enset.bigbang.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    private final CsrfTokenRepository csrfTokens;
    private final LoginRateLimiter limiter;
    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contexts,
                          SessionAuthenticationStrategy sessions, CsrfTokenRepository csrfTokens, LoginRateLimiter limiter) {
        this.authenticationManager = authenticationManager; this.contexts = contexts; this.sessions = sessions;
        this.csrfTokens = csrfTokens; this.limiter = limiter;
    }
    public record LoginRequest(@NotBlank @Size(max=100) String username, @NotBlank @Size(max=72) String password) { }
    public record Identity(java.util.UUID id, String username, Role role) { }
    private Identity identity(Authentication authentication) {
        SessionPrincipal principal = (SessionPrincipal) authentication.getPrincipal();
        return new Identity(principal.id(), principal.getUsername(), principal.role());
    }
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "parameterName", token.getParameterName(), "token", token.getToken());
    }
    @PostMapping("/admin/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        String key = request.getRemoteAddr();
        if (!limiter.tryAcquire(key)) return ResponseEntity.status(429).body(Map.of("status", 429, "error", "Too many attempts"));
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", 401, "error", "Invalid credentials"));
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(input.username(), input.password()));
            sessions.onAuthentication(authentication, request, response);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            limiter.success(key);
            return ResponseEntity.ok(identity(authentication));
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", 401, "error", "Invalid credentials"));
        }
    }
    @GetMapping("/me")
    public Identity me(Authentication authentication) { return identity(authentication); }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        csrfTokens.saveToken(null, request, response);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        // The cookie path matches the default context path and carries no session value.
        var cookie = new jakarta.servlet.http.Cookie("SESSION", "");
        cookie.setMaxAge(0); cookie.setHttpOnly(true); cookie.setSecure(request.isSecure()); cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        response.addCookie(cookie);
        return ResponseEntity.noContent().build();
    }
}
