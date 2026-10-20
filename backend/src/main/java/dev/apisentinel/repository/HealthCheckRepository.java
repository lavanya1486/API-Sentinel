package dev.apisentinel.repository;

import dev.apisentinel.domain.HealthCheck;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface HealthCheckRepository extends JpaRepository<HealthCheck, UUID> {
    Page<HealthCheck> findByEndpoint_IdAndCheckedAtAfterOrderByCheckedAtDesc(UUID endpointId, Instant after, Pageable pageable);

    interface LatestCheckView {
        UUID getEndpointId();
        Boolean getSuccessful();
        Integer getStatusCode();
        String getFailureDetail();
        Long getResponseTimeMs();
        Instant getCheckedAt();
    }

    @Query(value = """
        SELECT DISTINCT ON (endpoint_id)
            endpoint_id AS "endpointId", successful AS "successful", status_code AS "statusCode",
            failure_detail AS "failureDetail", response_time_ms AS "responseTimeMs", checked_at AS "checkedAt"
        FROM health_checks
        WHERE endpoint_id IN (:endpointIds)
        ORDER BY endpoint_id, checked_at DESC
        """, nativeQuery = true)
    List<LatestCheckView> findLatestForEndpoints(@Param("endpointIds") List<UUID> endpointIds);

    interface AggregateView {
        Long getTotalCount();
        Long getSuccessCount();
        Long getFailureCount();
        BigDecimal getAverageLatencyMs();
        Double getP95LatencyMs();
        Double getP99LatencyMs();
    }

    @Query(value = """
        SELECT count(*) AS "totalCount",
               count(*) FILTER (WHERE hc.successful) AS "successCount",
               count(*) FILTER (WHERE NOT hc.successful) AS "failureCount",
               coalesce(avg(hc.response_time_ms) FILTER (WHERE hc.successful), 0)::numeric AS "averageLatencyMs",
               coalesce(percentile_cont(0.95) WITHIN GROUP (ORDER BY hc.response_time_ms)
                    FILTER (WHERE hc.successful), 0)::double precision AS "p95LatencyMs",
               coalesce(percentile_cont(0.99) WITHIN GROUP (ORDER BY hc.response_time_ms)
                    FILTER (WHERE hc.successful), 0)::double precision AS "p99LatencyMs"
        FROM health_checks hc
        JOIN monitored_endpoints e ON e.id = hc.endpoint_id
        WHERE e.owner_id = :ownerId AND e.archived_at IS NULL
          AND hc.checked_at >= :fromTime
          AND (:endpointId IS NULL OR e.id = :endpointId)
        """, nativeQuery = true)
    AggregateView aggregate(@Param("ownerId") UUID ownerId, @Param("fromTime") Instant fromTime,
                            @Param("endpointId") UUID endpointId);

    interface SeriesView {
        Instant getBucketAt();
        BigDecimal getAverageLatencyMs();
        Long getSuccessCount();
        Long getFailureCount();
        Long getCheckCount();
    }

    @Query(value = """
        SELECT date_trunc(CAST(:bucket AS text), hc.checked_at) AS "bucketAt",
               coalesce(avg(hc.response_time_ms) FILTER (WHERE hc.successful), 0)::numeric AS "averageLatencyMs",
               count(*) FILTER (WHERE hc.successful) AS "successCount",
               count(*) FILTER (WHERE NOT hc.successful) AS "failureCount",
               count(*) AS "checkCount"
        FROM health_checks hc
        JOIN monitored_endpoints e ON e.id = hc.endpoint_id
        WHERE e.owner_id = :ownerId AND e.archived_at IS NULL
          AND hc.checked_at >= :fromTime
          AND (:endpointId IS NULL OR e.id = :endpointId)
        GROUP BY 1
        ORDER BY 1
        """, nativeQuery = true)
    List<SeriesView> series(@Param("ownerId") UUID ownerId, @Param("fromTime") Instant fromTime,
                            @Param("endpointId") UUID endpointId, @Param("bucket") String bucket);

    @Transactional
    @Modifying
    @Query("delete from HealthCheck h where h.checkedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
