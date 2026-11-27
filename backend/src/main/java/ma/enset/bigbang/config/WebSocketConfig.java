package ma.enset.bigbang.config;

import java.security.Principal;
import java.util.Arrays;
import java.util.Map;
import ma.enset.bigbang.auth.SessionPrincipal;
import ma.enset.bigbang.notification.RealtimeIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.session.Session;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.web.socket.config.annotation.AbstractSessionWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableScheduling
@EnableWebSocketMessageBroker
public class WebSocketConfig<S extends Session> extends AbstractSessionWebSocketMessageBrokerConfigurer<S> {
    private final String[] origins;
    private final long heartbeat;
    private final int messageSize, bufferSize, sendTimeout;
    private final ThreadPoolTaskScheduler scheduler;
    private final ma.enset.bigbang.notification.SessionGuard sessionGuard;
    public WebSocketConfig(@Value("${app.realtime.allowed-origins}") String origins,
            @Value("${app.realtime.heartbeat-ms:10000}") long heartbeat,
            @Value("${app.realtime.message-size-bytes:65536}") int messageSize,
            @Value("${app.realtime.send-buffer-bytes:262144}") int bufferSize,
            @Value("${app.realtime.send-timeout-ms:10000}") int sendTimeout,
            ThreadPoolTaskScheduler realtimeHeartbeatScheduler,
            ma.enset.bigbang.notification.SessionGuard sessionGuard) {
        this.origins = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (this.origins.length == 0 || Arrays.stream(this.origins).anyMatch(s -> s.contains("*") || s.equals("null")))
            throw new IllegalArgumentException("WebSocket origins must be explicit, non-wildcard origins");
        if (heartbeat < 1000 || messageSize < 1024 || bufferSize < messageSize || sendTimeout < 1000)
            throw new IllegalArgumentException("Invalid WebSocket transport configuration");
        this.heartbeat = heartbeat; this.messageSize = messageSize; this.bufferSize = bufferSize;
        this.sendTimeout = sendTimeout; this.scheduler = realtimeHeartbeatScheduler; this.sessionGuard = sessionGuard;
    }
    @Bean
    static ThreadPoolTaskScheduler realtimeHeartbeatScheduler() {
        var taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2); taskScheduler.setThreadNamePrefix("ws-heartbeat-");
        taskScheduler.setWaitForTasksToCompleteOnShutdown(false);
        return taskScheduler;
    }
    @Override protected void configureStompEndpoints(StompEndpointRegistry registry) {
        var httpSession = new HttpSessionHandshakeInterceptor();
        httpSession.setCopyAllAttributes(false);
        httpSession.setCopyHttpSessionId(true);
        httpSession.setCreateSession(false);
        registry.addEndpoint("/ws").addInterceptors(httpSession).setAllowedOrigins(origins)
            .setHandshakeHandler(new DefaultHandshakeHandler() {
                @Override protected Principal determineUser(ServerHttpRequest request, WebSocketHandler handler, Map<String, Object> attrs) {
                    if (!(request.getPrincipal() instanceof Authentication auth) || !auth.isAuthenticated()
                            || !(auth.getPrincipal() instanceof SessionPrincipal principal) || !principal.isEnabled()) return null;
                    var identity = new RealtimeIdentity(principal.role(), principal.id());
                    return UsernamePasswordAuthenticationToken.authenticated(identity, null, auth.getAuthorities());
                }
            });
    }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/topic", "/queue").setHeartbeatValue(new long[]{heartbeat, heartbeat}).setTaskScheduler(scheduler);
    }
    @Override public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        super.configureWebSocketTransport(registration);
        registration.setMessageSizeLimit(messageSize).setSendBufferSizeLimit(bufferSize).setSendTimeLimit(sendTimeout);
        registration.addDecoratorFactory(sessionGuard);
    }
    @Override public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(4).maxPoolSize(8).queueCapacity(500);
        registration.interceptors(sessionGuard);
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) {
        super.configureClientInboundChannel(registration);
        registration.taskExecutor().corePoolSize(4).maxPoolSize(8).queueCapacity(500);
        registration.interceptors(sessionGuard);
    }
}
