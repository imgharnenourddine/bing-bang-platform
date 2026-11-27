package ma.enset.bigbang.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {
    @Bean AuthorizationManager<Message<?>> messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages.simpTypeMatchers(SimpMessageType.CONNECT).authenticated()
            .simpTypeMatchers(SimpMessageType.DISCONNECT, SimpMessageType.UNSUBSCRIBE, SimpMessageType.HEARTBEAT).authenticated()
            .simpSubscribeDestMatchers("/topic/event", "/topic/draw", "/topic/wheel", "/topic/quiz").hasAnyRole("ADMIN", "PARTICIPANT")
            .simpSubscribeDestMatchers("/topic/admin").hasRole("ADMIN")
            .simpSubscribeDestMatchers("/user/queue/notifications").hasAnyRole("ADMIN", "PARTICIPANT")
            .anyMessage().denyAll();
        return messages.build();
    }
}
