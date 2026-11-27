package ma.enset.bigbang.config;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.messaging.web.csrf.XorCsrfChannelInterceptor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import static org.junit.jupiter.api.Assertions.*;

class StompCsrfTest {
    @Test void connectRequiresValidSessionToken() {
        CsrfToken raw = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "a-session-token");
        var request = new MockHttpServletRequest();
        new XorCsrfTokenRequestAttributeHandler().handle(request, new org.springframework.mock.web.MockHttpServletResponse(), () -> raw);
        var masked = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        var attributes = new HashMap<String, Object>();
        attributes.put(CsrfToken.class.getName(), raw);
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setSessionAttributes(attributes);
        headers.setNativeHeader(raw.getHeaderName(), masked.getToken());
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertNotNull(new XorCsrfChannelInterceptor().preSend(message, null));
        var missing = StompHeaderAccessor.create(StompCommand.CONNECT);
        missing.setSessionAttributes(attributes);
        var noToken = MessageBuilder.createMessage(new byte[0], missing.getMessageHeaders());
        assertThrows(RuntimeException.class, () -> new XorCsrfChannelInterceptor().preSend(noToken, null));
    }
}
