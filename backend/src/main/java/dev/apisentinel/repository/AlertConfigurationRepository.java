package dev.apisentinel.repository;

import dev.apisentinel.domain.AlertConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertConfigurationRepository extends JpaRepository<AlertConfiguration, UUID> {
    Optional<AlertConfiguration> findByEndpoint_Id(UUID endpointId);

    @Query("select a from AlertConfiguration a join fetch a.endpoint e where e.owner.id = :ownerId and e.archivedAt is null order by a.updatedAt desc")
    List<AlertConfiguration> findAllForOwner(@Param("ownerId") UUID ownerId);
}
