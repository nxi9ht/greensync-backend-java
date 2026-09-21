package th.ac.rmutt.greensync.subscriptions;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import th.ac.rmutt.greensync.organizations.Organization;

@Entity
@Table(name = "organization_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationSubscription {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "org_subscription_id")
  private Integer id;

  @Column(name = "org_id", insertable = false, updatable = false)
  private Integer orgId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "org_id")
  private Organization organization;

  @Column(name = "plan_id", insertable = false, updatable = false)
  private Integer planId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "plan_id")
  private SubscriptionPlan plan;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  private String status;

  @Column(name = "auto_renew", nullable = false)
  private boolean autoRenew = false;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;
}
