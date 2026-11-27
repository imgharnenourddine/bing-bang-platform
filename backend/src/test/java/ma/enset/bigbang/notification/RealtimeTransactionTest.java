package ma.enset.bigbang.notification;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RealtimeTransactionTest {
    @Configuration @EnableTransactionManagement
    static class TestConfig {
        @Bean PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
                protected Object doGetTransaction() { return new Object(); }
                protected boolean isExistingTransaction(Object transaction) { return active.get(); }
                protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) { active.set(true); }
                protected void doCommit(DefaultTransactionStatus status) { }
                protected void doRollback(DefaultTransactionStatus status) { }
                protected void doCleanupAfterCompletion(Object transaction) { active.remove(); }
            };
        }
        @Bean SimpMessagingTemplate messaging() { return mock(SimpMessagingTemplate.class); }
        @Bean RealtimeEvents events(org.springframework.context.ApplicationEventPublisher publisher) {
            return new RealtimeEvents(publisher, mock(ma.enset.bigbang.participant.ParticipantRepository.class));
        }
        @Bean RealtimeDelivery delivery(SimpMessagingTemplate messaging) { return new RealtimeDelivery(messaging); }
    }
    @Test void sendsOnlyAfterCommitAndNotAfterRollback() {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var events = context.getBean(RealtimeEvents.class);
            var messaging = context.getBean(SimpMessagingTemplate.class);
            var payload = new RealtimePayload.ParticipantRegistered(UUID.randomUUID(), 1, "A", "B");
            tx.executeWithoutResult(status -> {
                events.participantRegistered(payload);
                verifyNoInteractions(messaging);
                status.setRollbackOnly();
            });
            verifyNoInteractions(messaging);
            tx.executeWithoutResult(status -> {
                events.participantRegistered(payload);
                verifyNoInteractions(messaging);
            });
            verify(messaging).convertAndSend(eq("/topic/admin"), any(EventEnvelope.class));
        }
    }
    @Test void drawResetIsDeliveredOnlyAfterCommit() {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var events = context.getBean(RealtimeEvents.class);
            var messaging = context.getBean(SimpMessagingTemplate.class);
            tx.executeWithoutResult(status -> { events.drawReset(new RealtimePayload.DrawReset(1, 2)); status.setRollbackOnly(); });
            verifyNoInteractions(messaging);
            tx.executeWithoutResult(status -> { events.drawReset(new RealtimePayload.DrawReset(1, 2)); verifyNoInteractions(messaging); });
            verify(messaging).convertAndSend(eq("/topic/draw"), argThat((Object value) ->
                    value instanceof EventEnvelope<?> envelope && envelope.type() == EventType.DRAW_RESET));
        }
    }
}
