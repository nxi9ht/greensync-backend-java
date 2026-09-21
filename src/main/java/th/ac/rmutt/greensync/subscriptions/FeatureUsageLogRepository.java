package th.ac.rmutt.greensync.subscriptions;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureUsageLogRepository extends JpaRepository<FeatureUsageLog, Integer> {

  Optional<FeatureUsageLog> findByOrgIdAndFeatureCodeAndUsageMonthAndUsageYear(
      Integer orgId, String featureCode, Integer usageMonth, Integer usageYear);

  List<FeatureUsageLog> findByOrgIdAndUsageMonthAndUsageYearOrderByFeatureCodeAsc(
      Integer orgId, Integer usageMonth, Integer usageYear);
}
