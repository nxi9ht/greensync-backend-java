package th.ac.rmutt.greensync.subscriptions;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "feature_usage_logs")
@Getter
@Setter
@NoArgsConstructor
public class FeatureUsageLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "feature_usage_log_id")
  private Integer id;

  @Column(name = "org_id")
  private Integer orgId;

  @Column(name = "feature_code")
  private String featureCode;

  @Column(name = "usage_count", nullable = false)
  private Integer usageCount = 0;

  @Column(name = "usage_date")
  private LocalDate usageDate = LocalDate.now();

  @Column(name = "usage_month")
  private Integer usageMonth;

  @Column(name = "usage_year")
  private Integer usageYear;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;
}
