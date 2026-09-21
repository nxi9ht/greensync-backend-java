package th.ac.rmutt.greensync.subscriptions;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationSubscriptionRepository extends JpaRepository<OrganizationSubscription, Integer> {

  Optional<OrganizationSubscription> findByOrgIdAndStatus(Integer orgId, String status);

  Optional<OrganizationSubscription> findByOrgId(Integer orgId);
}
