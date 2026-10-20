package dev.apisentinel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "monitored_endpoints")
public class MonitoredEndpoint {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "url_host", nullable = false, length = 253)
    private String urlHost;

    @Column(name = "url_ciphertext", nullable = false, columnDefinition = "text")
    private String urlCiphertext;

    @Column(name = "headers_ciphertext", columnDefinition = "text")
    private String headersCiphertext;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(name = "expected_status_codes", nullable = false, length = 200)
    private String expectedStatusCodes;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "retry_backoff_ms", nullable = false)
    private int retryBackoffMs;

    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold;

    @Column(name = "recovery_threshold", nullable = false)
    private int recoveryThreshold;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "consecutive_successes", nullable = false)
    private int consecutiveSuccesses;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "next_check_at", nullable = false)
    private Instant nextCheckAt;

    @Column(name = "check_lease_until")
    private Instant checkLeaseUntil;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MonitoredEndpoint() { }

    public MonitoredEndpoint(AppUser owner, String name, String urlHost, String urlCiphertext, String headersCiphertext,
                             String method, String expectedStatusCodes, int timeoutMs, int intervalSeconds,
                             int retryCount, int retryBackoffMs, int failureThreshold, int recoveryThreshold) {
        this.owner = owner;
        this.name = name;
        this.urlHost = urlHost;
        this.urlCiphertext = urlCiphertext;
        this.headersCiphertext = headersCiphertext;
        this.method = method;
        this.expectedStatusCodes = expectedStatusCodes;
        this.timeoutMs = timeoutMs;
        this.intervalSeconds = intervalSeconds;
        this.retryCount = retryCount;
        this.retryBackoffMs = retryBackoffMs;
        this.failureThreshold = failureThreshold;
        this.recoveryThreshold = recoveryThreshold;
        this.enabled = true;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (nextCheckAt == null) nextCheckAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrlHost() { return urlHost; }
    public void setUrlHost(String urlHost) { this.urlHost = urlHost; }
    public String getUrlCiphertext() { return urlCiphertext; }
    public void setUrlCiphertext(String urlCiphertext) { this.urlCiphertext = urlCiphertext; }
    public String getHeadersCiphertext() { return headersCiphertext; }
    public void setHeadersCiphertext(String headersCiphertext) { this.headersCiphertext = headersCiphertext; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getExpectedStatusCodes() { return expectedStatusCodes; }
    public void setExpectedStatusCodes(String expectedStatusCodes) { this.expectedStatusCodes = expectedStatusCodes; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public int getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(int retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }
    public int getFailureThreshold() { return failureThreshold; }
    public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
    public int getRecoveryThreshold() { return recoveryThreshold; }
    public void setRecoveryThreshold(int recoveryThreshold) { this.recoveryThreshold = recoveryThreshold; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
    public int getConsecutiveSuccesses() { return consecutiveSuccesses; }
    public void setConsecutiveSuccesses(int consecutiveSuccesses) { this.consecutiveSuccesses = consecutiveSuccesses; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getNextCheckAt() { return nextCheckAt; }
    public void setNextCheckAt(Instant nextCheckAt) { this.nextCheckAt = nextCheckAt; }
    public Instant getCheckLeaseUntil() { return checkLeaseUntil; }
    public void setCheckLeaseUntil(Instant checkLeaseUntil) { this.checkLeaseUntil = checkLeaseUntil; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
