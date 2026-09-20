package th.ac.rmutt.greensync.carbonlogs;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarbonLogRepository extends JpaRepository<CarbonLog, Integer> {

  List<CarbonLog> findByOrganizationIdOrderByYearDescMonthDescCreatedAtDesc(
      Integer orgId, Pageable pageable);

  Optional<CarbonLog> findByIdAndOrganizationId(Integer id, Integer orgId);

  // Date-range filtering for the trend endpoint happens in the service layer (Java streams)
  // since start/end are each independently optional in the original NestJS query builder.
  List<CarbonLog> findByOrganizationIdOrderByCreatedAtAsc(Integer orgId);
}
