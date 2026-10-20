package dev.apisentinel.repository;

import dev.apisentinel.domain.MonitoredEndpoint;
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

public interface MonitoredEndpointRepository extends JpaRepository<MonitoredEndpoint, UUID> {
    @Query("""
        select e from MonitoredEndpoint e
        where e.owner.id = :ownerId and e.archivedAt is null
          and (lower(e.name) like lower(concat('%', :query, '%'))
               or lower(e.urlHost) like lower(concat('%', :query, '%')))
        """)
    Page<MonitoredEndpoint> searchOwned(@Param("ownerId") UUID ownerId, @Param("query") String query, Pageable pageable);

    @Query("""
        select e from MonitoredEndpoint e
        where e.owner.id = :ownerId and e.archivedAt is null
        """)
    Page<MonitoredEndpoint> findOwned(@Param("ownerId") UUID ownerId, Pageable pageable);

    Optional<MonitoredEndpoint> findByIdAndOwner_IdAndArchivedAtIsNull(UUID id, UUID ownerId);

    @Transactional(readOnly = true)
    @Query("select e from MonitoredEndpoint e where e.enabled = true and e.archivedAt is null and e.nextCheckAt <= :now and (e.checkLeaseUntil is null or e.checkLeaseUntil <= :now) order by e.nextCheckAt asc")
    List<MonitoredEndpoint> findDue(@Param("now") Instant now, Pageable pageable);

    @Transactional
    @Modifying
    @Query("update MonitoredEndpoint e set e.nextCheckAt = :leaseUntil, e.checkLeaseUntil = :leaseUntil where e.id = :id and e.enabled = true and e.archivedAt is null and e.nextCheckAt <= :now and (e.checkLeaseUntil is null or e.checkLeaseUntil <= :now)")
    int claimDue(@Param("id") UUID id, @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Transactional
    @Modifying
    @Query("update MonitoredEndpoint e set e.nextCheckAt = :leaseUntil, e.checkLeaseUntil = :leaseUntil where e.id = :id and e.enabled = true and e.archivedAt is null and (e.checkLeaseUntil is null or e.checkLeaseUntil <= :now)")
    int claimManual(@Param("id") UUID id, @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Transactional
    @Modifying
    @Query("update MonitoredEndpoint e set e.nextCheckAt = :nextCheckAt, e.checkLeaseUntil = null where e.id = :id")
    int releaseClaim(@Param("id") UUID id, @Param("nextCheckAt") Instant nextCheckAt);

    @Transactional
    @Modifying
    @Query("update MonitoredEndpoint e set e.nextCheckAt = :nextCheckAt where e.id = :id and e.archivedAt is null")
    int setNextCheck(@Param("id") UUID id, @Param("nextCheckAt") Instant nextCheckAt);

    long countByOwner_IdAndArchivedAtIsNull(UUID ownerId);
    long countByOwner_IdAndEnabledTrueAndArchivedAtIsNull(UUID ownerId);
}
