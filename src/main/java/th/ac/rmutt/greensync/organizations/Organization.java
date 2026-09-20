package th.ac.rmutt.greensync.organizations;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import th.ac.rmutt.greensync.users.User;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "org_id")
  private Integer id;

  @Column(nullable = false)
  private String name;

  @Column(name = "tax_id")
  private String taxId;

  @Column(name = "industry_type")
  private String industryType;

  @Column(name = "number_of_employees")
  private Integer numberOfEmployees;

  @Column(name = "total_floor_area")
  private Double totalFloorArea;

  @Column(name = "working_hours_per_year")
  private Integer workingHoursPerYear;

  @Column(name = "base_year")
  private Integer baseYear;

  @Column(name = "target_reduction_percent")
  private Double targetReductionPercent;

  @Column(name = "target_year")
  private Integer targetYear;

  @Column(name = "industry_benchmark_value")
  private Double industryBenchmarkValue;

  @Column(name = "carbon_standard")
  private String carbonStandard;

  @Column(name = "current_green_status")
  private String currentGreenStatus;

  @Column(name = "stripe_customer_id")
  private String stripeCustomerId;

  @Column(name = "stripe_subscription_id")
  private String stripeSubscriptionId;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "cached_executive_summary", columnDefinition = "text")
  private String cachedExecutiveSummary;

  @Column(name = "cached_recommendations", columnDefinition = "text")
  private String cachedRecommendations;

  @Column(name = "last_summary_hash")
  private String lastSummaryHash;

  @Column(name = "last_recommendations_hash")
  private String lastRecommendationsHash;

  @Column(name = "last_summary_analyzed_at")
  private Instant lastSummaryAnalyzedAt;

  @Column(name = "last_recommendations_analyzed_at")
  private Instant lastRecommendationsAnalyzedAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY)
  private List<User> users = new ArrayList<>();
}
