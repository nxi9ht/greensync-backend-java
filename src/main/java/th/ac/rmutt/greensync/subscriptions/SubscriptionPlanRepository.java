package th.ac.rmutt.greensync.subscriptions;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Integer> {

  List<SubscriptionPlan> findByActiveTrueOrderByPricePerMonthAsc();

  java.util.Optional<SubscriptionPlan> findByIdAndActiveTrue(Integer id);
}
