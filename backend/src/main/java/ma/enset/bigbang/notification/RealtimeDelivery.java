package ma.enset.bigbang.notification;

import ma.enset.bigbang.auth.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RealtimeDelivery {
    private static final Logger log = LoggerFactory.getLogger(RealtimeDelivery.class);
    private final SimpMessagingTemplate messaging;
    public RealtimeDelivery(SimpMessagingTemplate messaging) { this.messaging = messaging; }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(RealtimeEvents.Pending pending) {
        try {
            switch (pending.target()) {
                case EVENT -> messaging.convertAndSend("/topic/event", pending.event());
                case DRAW -> messaging.convertAndSend("/topic/draw", pending.event());
                case WHEEL -> messaging.convertAndSend("/topic/wheel", pending.event());
                case QUIZ -> messaging.convertAndSend("/topic/quiz", pending.event());
                case ADMIN -> messaging.convertAndSend("/topic/admin", pending.event());
                case PARTICIPANT -> messaging.convertAndSendToUser(RealtimeIdentity.name(Role.PARTICIPANT, pending.recipient()), "/queue/notifications", pending.event());
            }
        } catch (RuntimeException ex) {
            log.warn("Real-time delivery failed after commit; clients must resynchronize");
        }
    }
}
