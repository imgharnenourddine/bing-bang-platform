package ma.enset.bigbang.participant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import ma.enset.bigbang.auth.Role;
import ma.enset.bigbang.auth.SessionPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
public class ParticipantController {
    private final ParticipantService participants;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;
    public ParticipantController(ParticipantService participants, SessionAuthenticationStrategy sessions, SecurityContextRepository contexts) {
        this.participants = participants; this.sessions = sessions; this.contexts = contexts;
    }
    @PostMapping("/api/participants/register")
    public ResponseEntity<ParticipantResponse> register(@Valid @RequestBody RegistrationRequest request,
            Authentication current, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        if (current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken)) {
            if (!(current.getPrincipal() instanceof SessionPrincipal principal) || principal.role() != Role.PARTICIPANT)
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            return ResponseEntity.ok(participants.findById(principal.id()));
        }
        var session = servletRequest.getSession(false);
        // CSRF token acquisition establishes this session before the POST.
        if (session == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        var result = participants.register(session.getId(), request);
        var principal = new SessionPrincipal(result.participant().id(), result.participant().id().toString(), Role.PARTICIPANT, true, null);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        sessions.onAuthentication(authentication, servletRequest, servletResponse);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, servletRequest, servletResponse);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.participant());
    }
    @GetMapping("/api/participants/me")
    public ParticipantResponse me(Authentication authentication) {
        return participants.findById(((SessionPrincipal) authentication.getPrincipal()).id());
    }
    @GetMapping("/api/admin/participants")
    public Page<ParticipantResponse> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return participants.list(page, size);
    }
    @GetMapping("/api/admin/participants/count")
    public Map<String, Long> count() { return Map.of("count", participants.count()); }
}
