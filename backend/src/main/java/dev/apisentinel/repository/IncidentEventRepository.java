package dev.apisentinel.repository;

import dev.apisentinel.domain.IncidentEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentEventRepository extends JpaRepository<IncidentEvent, UUID> {
    List<IncidentEvent> findByIncident_IdOrderByOccurredAtAsc(UUID incidentId);
}
