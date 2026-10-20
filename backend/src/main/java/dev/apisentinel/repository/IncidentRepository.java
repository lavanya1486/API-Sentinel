package dev.apisentinel.repository;

import dev.apisentinel.domain.Incident;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    @Query("""
        select i from Incident i join i.endpoint e
        where e.owner.id = :ownerId and e.archivedAt is null
          and (:status is null or i.status = :status)
          and (:endpointId is null or e.id = :endpointId)
        """)
    Page<Incident> searchOwned(@Param("ownerId") UUID ownerId, @Param("status") String status,
                               @Param("endpointId") UUID endpointId, Pageable pageable);

    Optional<Incident> findByIdAndEndpoint_Owner_IdAndEndpoint_ArchivedAtIsNull(UUID id, UUID ownerId);
    Optional<Incident> findFirstByEndpoint_IdAndStatus(UUID endpointId, String status);
    long countByEndpoint_Owner_IdAndStatusAndEndpoint_ArchivedAtIsNull(UUID ownerId, String status);
}
