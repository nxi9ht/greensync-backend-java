package th.ac.rmutt.greensync.auditlogs;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {

  @Query(
      "select l from AuditLog l where (:orgId is null or l.user.organization.id = :orgId) "
          + "order by l.createdAt desc")
  List<AuditLog> search(@Param("orgId") Integer orgId, Pageable pageable);
}
