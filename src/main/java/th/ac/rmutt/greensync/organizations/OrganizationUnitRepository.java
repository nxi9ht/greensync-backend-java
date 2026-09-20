package th.ac.rmutt.greensync.organizations;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, Integer> {

  List<OrganizationUnit> findByOrgIdOrderByCreatedAtAsc(Integer orgId);
}
