package dev.apisentinel.service;

import dev.apisentinel.domain.NotificationDelivery;
import dev.apisentinel.repository.NotificationDeliveryRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private final NotificationDeliveryRepository deliveries;
    private final NotificationDispatchService dispatchService;

    public NotificationDispatcher(NotificationDeliveryRepository deliveries, NotificationDispatchService dispatchService) {
        this.deliveries = deliveries;
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${app.notifications.scan-delay-ms:5000}")
    public void dispatch() {
        try {
            deliveries.reclaimStaleInProgress();
            List<NotificationDelivery> due = deliveries.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                List.of("PENDING", "RETRYING"), Instant.now());
            for (NotificationDelivery item : due) {
                try {
                    dispatchService.deliver(dispatchService.claim(item.getId()));
                } catch (RuntimeException ex) {
                    log.warn("Notification worker failed for delivery {} ({})", item.getId(), ex.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException ex) {
            log.error("Notification queue scan failed ({})", ex.getClass().getSimpleName());
        }
    }
}
