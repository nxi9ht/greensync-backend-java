package th.ac.rmutt.greensync.carbonlogs;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import th.ac.rmutt.greensync.organizations.Organization;
import th.ac.rmutt.greensync.organizations.OrganizationUnit;

@Entity
@Table(name = "carbon_activity_logs")
@Getter
@Setter
@NoArgsConstructor
public class CarbonLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "carbon_log_id")
  private Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "emission_factor_id")
  private EmissionFactor emissionFactor;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "org_id")
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "org_unit_id")
  private OrganizationUnit organizationUnit;

  @Column(name = "activity_type")
  private String activityType;

  private Integer month;

  private Integer year;

  @Column(name = "usage_amount")
  private Double usageAmount;

  @Column(name = "total_emission")
  private Double totalEmission;

  @Column(name = "evidence_url", columnDefinition = "text")
  private String evidenceUrl;

  @Column(name = "data_source")
  private String dataSource;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;
}
