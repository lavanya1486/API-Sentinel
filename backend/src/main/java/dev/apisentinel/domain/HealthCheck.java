package dev.apisentinel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "health_checks")
public class HealthCheck {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private MonitoredEndpoint endpoint;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(name = "response_time_ms", nullable = false)
    private long responseTimeMs;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(nullable = false)
    private boolean successful;

    @Column(name = "failure_detail", length = 200)
    private String failureDetail;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    protected HealthCheck() { }

    public HealthCheck(MonitoredEndpoint endpoint, long responseTimeMs, Integer statusCode,
                       boolean successful, String failureDetail, int attemptCount) {
        this.endpoint = endpoint;
        this.responseTimeMs = responseTimeMs;
        this.statusCode = statusCode;
        this.successful = successful;
        this.failureDetail = failureDetail;
        this.attemptCount = attemptCount;
    }

    @PrePersist
    void onCreate() { if (checkedAt == null) checkedAt = Instant.now(); }

    public UUID getId() { return id; }
    public MonitoredEndpoint getEndpoint() { return endpoint; }
    public Instant getCheckedAt() { return checkedAt; }
    public long getResponseTimeMs() { return responseTimeMs; }
    public Integer getStatusCode() { return statusCode; }
    public boolean isSuccessful() { return successful; }
    public String getFailureDetail() { return failureDetail; }
    public int getAttemptCount() { return attemptCount; }
}
