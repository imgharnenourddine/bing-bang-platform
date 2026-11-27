package ma.enset.bigbang.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketAuthorizationTest {
    private final AuthorizationManager<Message<?>> policy = new WebSocketSecurityConfig()
            .messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.builder());
    private Message<byte[]> message(SimpMessageType type, String destination) {
        var headers = SimpMessageHeaderAccessor.create(type);
        if (destination != null) headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
    private boolean allowed(String role, SimpMessageType type, String destination) {
        var auth = new TestingAuthenticationToken("user", null, role);
        return policy.check(() -> auth, message(type, destination)).isGranted();
    }
    @Test void adminAndParticipantHaveOnlyApprovedSubscriptions() {
        assertTrue(allowed("ROLE_ADMIN", SimpMessageType.SUBSCRIBE, "/topic/admin"));
        assertFalse(allowed("ROLE_PARTICIPANT", SimpMessageType.SUBSCRIBE, "/topic/admin"));
        assertTrue(allowed("ROLE_PARTICIPANT", SimpMessageType.SUBSCRIBE, "/topic/event"));
        assertTrue(allowed("ROLE_PARTICIPANT", SimpMessageType.SUBSCRIBE, "/user/queue/notifications"));
        assertFalse(allowed("ROLE_PARTICIPANT", SimpMessageType.SUBSCRIBE, "/queue/notifications-user-other"));
        assertFalse(allowed("ROLE_ADMIN", SimpMessageType.SUBSCRIBE, "/topic/unknown"));
    }
    @Test void clientsCannotSendOrConnectWithoutAuthentication() {
        assertFalse(allowed("ROLE_ADMIN", SimpMessageType.MESSAGE, "/topic/event"));
        assertFalse(allowed("ROLE_ADMIN", SimpMessageType.MESSAGE, "/app/do"));
        assertFalse(policy.check(() -> null, message(SimpMessageType.CONNECT, null)).isGranted());
    }
}
