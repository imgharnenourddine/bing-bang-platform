package ma.enset.bigbang.notification;

import java.util.UUID;
import ma.enset.bigbang.auth.Role;
import ma.enset.bigbang.auth.SessionPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionGuardTest {
    @SuppressWarnings("unchecked")
    @Test void rejectsExpiredDeletedAndWrongIdentity() {
        SessionRepository<Session> repository = mock(SessionRepository.class);
        UUID id = UUID.randomUUID();
        var user = new SessionPrincipal(id, "participant", Role.PARTICIPANT, true, null);
        var httpAuth = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        var session = new MapSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(httpAuth));
        when(repository.findById("test-session")).thenReturn(session, session, null);
        var guard = new SessionGuard(repository);
        var wsAuth = UsernamePasswordAuthenticationToken.authenticated(new RealtimeIdentity(Role.PARTICIPANT, id), null, user.getAuthorities());
        var other = UsernamePasswordAuthenticationToken.authenticated(new RealtimeIdentity(Role.PARTICIPANT, UUID.randomUUID()), null, user.getAuthorities());
        assertTrue(guard.valid("test-session", wsAuth));
        assertFalse(guard.valid("test-session", other));
        assertFalse(guard.valid("test-session", wsAuth));
    }
    @SuppressWarnings("unchecked")
    @Test void rejectsHandshakeWhenSessionDoesNotExist() throws Exception {
        SessionRepository<Session> repository = mock(SessionRepository.class);
        var guard = new SessionGuard(repository);
        var handler = mock(org.springframework.web.socket.WebSocketHandler.class);
        var socket = mock(org.springframework.web.socket.WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(java.util.Map.of());
        guard.decorate(handler).afterConnectionEstablished(socket);
        verify(socket).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(handler);
    }
}
