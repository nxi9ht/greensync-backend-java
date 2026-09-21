package th.ac.rmutt.greensync.subscriptions;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import th.ac.rmutt.greensync.organizations.Organization;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "invoice_id")
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

  private Double amount;

  private String status;

  @Column(name = "reference_number")
  private String referenceNumber;

  @Column(columnDefinition = "text")
  private String notes;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;
}
