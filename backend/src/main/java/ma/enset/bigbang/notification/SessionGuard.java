package ma.enset.bigbang.notification;

import java.io.IOException;
import java.security.Principal;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/** Rechecks the backing HTTP session, including on outbound delivery, and closes stale sockets. */
@Component
public class SessionGuard implements ChannelInterceptor, WebSocketHandlerDecoratorFactory {
    private final SessionRepository<? extends Session> sessions;
    private final ConcurrentHashMap<String, WebSocketSession> sockets = new ConcurrentHashMap<>();
    public SessionGuard(SessionRepository<? extends Session> sessions) { this.sessions = sessions; }
    boolean valid(String httpSessionId, Principal principal) {
        if (httpSessionId == null || !(principal instanceof Authentication wsAuth)
                || !(wsAuth.getPrincipal() instanceof RealtimeIdentity wsIdentity)) return false;
        Session session = sessions.findById(httpSessionId);
        if (session == null || session.isExpired()) return false;
        SecurityContext context = session.getAttribute("SPRING_SECURITY_CONTEXT");
        if (context == null || context.getAuthentication() == null) return false;
        Authentication auth = context.getAuthentication();
        if (!auth.isAuthenticated() || !(auth.getPrincipal() instanceof ma.enset.bigbang.auth.SessionPrincipal user)) return false;
        return user.isEnabled() && user.id().equals(wsIdentity.id()) && user.role() == wsIdentity.role();
    }
    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = SimpMessageHeaderAccessor.wrap(message);
        if (headers.getSessionId() == null) return message;
        WebSocketSession socket = sockets.get(headers.getSessionId());
        try {
            if (socket == null || !valid((String) socket.getAttributes().get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME), socket.getPrincipal())) return null;
            return message;
        } catch (RuntimeException ex) { return null; }
    }
    @Override public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                String httpId = (String) session.getAttributes().get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);
                if (!valid(httpId, session.getPrincipal())) { session.close(CloseStatus.POLICY_VIOLATION); return; }
                sockets.put(session.getId(), session);
                try { super.afterConnectionEstablished(session); } catch (Exception e) { sockets.remove(session.getId()); throw e; }
            }
            @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sockets.remove(session.getId()); super.afterConnectionClosed(session, status);
            }
        };
    }
    @Scheduled(fixedDelayString = "${app.realtime.session-check-ms:30000}")
    public void closeInvalidSessions() {
        sockets.forEach((id, socket) -> {
            try {
                if (!socket.isOpen() || !valid((String) socket.getAttributes().get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME), socket.getPrincipal())) {
                    sockets.remove(id); if (socket.isOpen()) socket.close(CloseStatus.POLICY_VIOLATION);
                }
            } catch (Exception e) {
                // On repository errors, fail closed and avoid logging session data.
                sockets.remove(id); try { socket.close(CloseStatus.SERVER_ERROR); } catch (IOException ignored) { }
            }
        });
    }
}
