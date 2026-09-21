package th.ac.rmutt.greensync.subscriptions;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationSubscriptionRepository extends JpaRepository<OrganizationSubscription, Integer> {

  Optional<OrganizationSubscription> findByOrgIdAndStatus(Integer orgId, String status);

  Optional<OrganizationSubscription> findByOrgId(Integer orgId);

  @Query("select s from OrganizationSubscription s left join fetch s.plan")
  List<OrganizationSubscription> findAllWithPlan();
}
