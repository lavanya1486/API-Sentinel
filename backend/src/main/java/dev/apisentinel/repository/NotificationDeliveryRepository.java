package dev.apisentinel.repository;

import dev.apisentinel.domain.NotificationDelivery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
    @Query(value = "select d from NotificationDelivery d join d.alertConfiguration a join a.endpoint e where e.owner.id = :ownerId and e.archivedAt is null",
           countQuery = "select count(d) from NotificationDelivery d join d.alertConfiguration a join a.endpoint e where e.owner.id = :ownerId and e.archivedAt is null")
    Page<NotificationDelivery> findAllForOwner(@Param("ownerId") UUID ownerId, Pageable pageable);

    Optional<NotificationDelivery> findByIdAndAlertConfiguration_Endpoint_Owner_Id(UUID id, UUID ownerId);

    @Transactional(readOnly = true)
    List<NotificationDelivery> findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(List<String> statuses, Instant now);

    @Transactional
    @Modifying
    @Query(value = "UPDATE notification_deliveries SET status = 'IN_PROGRESS', attempts = attempts + 1, last_attempt_at = now() WHERE id = :id AND status IN ('PENDING','RETRYING') AND next_attempt_at <= now()", nativeQuery = true)
    int claimForDelivery(@Param("id") UUID id);

    @Transactional
    @Modifying
    @Query(value = "UPDATE notification_deliveries SET status = 'RETRYING', next_attempt_at = now(), last_error = 'Delivery worker timed out' WHERE status = 'IN_PROGRESS' AND last_attempt_at < now() - interval '10 minutes'", nativeQuery = true)
    int reclaimStaleInProgress();
}
