package dev.apisentinel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_configurations")
public class AlertConfiguration {
    @Id
    private UUID id = UUID.randomUUID();

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false, unique = true)
    private MonitoredEndpoint endpoint;

    @Column(name = "webhook_url_ciphertext", columnDefinition = "text")
    private String webhookUrlCiphertext;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "event_types", nullable = false, length = 100)
    private String eventTypes = "INCIDENT_OPENED,INCIDENT_RESOLVED";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AlertConfiguration() { }

    public AlertConfiguration(MonitoredEndpoint endpoint, String webhookUrlCiphertext, boolean enabled, String eventTypes) {
        this.endpoint = endpoint;
        this.webhookUrlCiphertext = webhookUrlCiphertext;
        this.enabled = enabled;
        this.eventTypes = eventTypes;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public MonitoredEndpoint getEndpoint() { return endpoint; }
    public String getWebhookUrlCiphertext() { return webhookUrlCiphertext; }
    public void setWebhookUrlCiphertext(String webhookUrlCiphertext) { this.webhookUrlCiphertext = webhookUrlCiphertext; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEventTypes() { return eventTypes; }
    public void setEventTypes(String eventTypes) { this.eventTypes = eventTypes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
