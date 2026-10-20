package dev.apisentinel.service;

import dev.apisentinel.config.AppProperties;
import dev.apisentinel.domain.NotificationDelivery;
import dev.apisentinel.repository.NotificationDeliveryRepository;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDispatchService {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);
    private final NotificationDeliveryRepository deliveries;
    private final SecretCrypto crypto;
    private final SafeHttpClient http;
    private final AppProperties properties;

    public NotificationDispatchService(NotificationDeliveryRepository deliveries, SecretCrypto crypto,
                                       SafeHttpClient http, AppProperties properties) {
        this.deliveries = deliveries;
        this.crypto = crypto;
        this.http = http;
        this.properties = properties;
    }

    @Transactional
    public Job claim(UUID id) {
        if (deliveries.claimForDelivery(id) != 1) return null;
        NotificationDelivery item = deliveries.findById(id).orElse(null);
        if (item == null) return null;
        var alert = item.getAlertConfiguration();
        String webhook = null;
        String setupError = null;
        try {
            if (alert.getWebhookUrlCiphertext() != null) webhook = crypto.decrypt(alert.getWebhookUrlCiphertext());
        } catch (RuntimeException ex) {
            setupError = "Webhook configuration unavailable";
        }
        if (webhook == null && setupError == null) setupError = "Webhook configuration unavailable";
        return new Job(item.getId(), item.getAttempts(), item.getPayloadJson(), webhook,
            alert.isEnabled(), setupError);
    }

    public void deliver(Job job) {
        if (job == null) return;
        if (!job.alertEnabled()) {
            finishFailure(job, "Alert configuration is disabled", false);
            return;
        }
        if (job.setupError() != null) {
            finishFailure(job, job.setupError(), false);
            return;
        }
        try {
            SafeHttpClient.HttpResult result = http.sendWebhook(job.webhookUrl(), job.payloadJson(), 5000);
            int status = result.statusCode();
            if (status >= 200 && status < 300) {
                finishSuccess(job.id());
            } else {
                boolean retryable = status == 429 || status >= 500;
                finishFailure(job, "Webhook returned HTTP " + status, retryable);
            }
        } catch (BlockedTargetException | BlockedDnsException ex) {
            finishFailure(job, "Webhook target blocked by SSRF policy", false);
        } catch (SocketTimeoutException ex) {
            finishFailure(job, "Webhook request timed out", true);
        } catch (UnknownHostException ex) {
            finishFailure(job, "Webhook DNS lookup failed", true);
        } catch (IOException ex) {
            finishFailure(job, "Webhook network request failed", true);
        } catch (RuntimeException ex) {
            log.warn("Webhook delivery failed for notification {} ({})", job.id(), ex.getClass().getSimpleName());
            finishFailure(job, "Webhook delivery failed", true);
        }
    }

    @Transactional
    public void finishSuccess(UUID id) {
        NotificationDelivery item = deliveries.findById(id).orElse(null);
        if (item == null) return;
        item.setStatus("SENT");
        item.setLastError(null);
        item.setDeliveredAt(Instant.now());
        deliveries.save(item);
    }

    @Transactional
    public void finishFailure(Job job, String safeReason, boolean retryable) {
        NotificationDelivery item = deliveries.findById(job.id()).orElse(null);
        if (item == null) return;
        int maxAttempts = Math.max(1, Math.min(5, properties.notifications().maxAttempts()));
        boolean shouldRetry = retryable && item.getAttempts() < maxAttempts;
        item.setLastError(safeReason.length() > 190 ? safeReason.substring(0, 190) : safeReason);
        if (shouldRetry) {
            item.setStatus("RETRYING");
            long multiplier = 1L << Math.min(10, Math.max(0, item.getAttempts() - 1));
            long delaySeconds = Math.min(3600, Math.max(1, properties.notifications().baseBackoffSeconds()) * multiplier);
            item.setNextAttemptAt(Instant.now().plusSeconds(delaySeconds));
        } else {
            item.setStatus("FAILED");
            item.setNextAttemptAt(Instant.now());
        }
        deliveries.save(item);
    }

    public record Job(UUID id, int attempts, String payloadJson, String webhookUrl, boolean alertEnabled, String setupError) { }
}
