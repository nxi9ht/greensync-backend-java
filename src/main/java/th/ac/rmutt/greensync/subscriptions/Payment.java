package th.ac.rmutt.greensync.subscriptions;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import th.ac.rmutt.greensync.organizations.Organization;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "payment_id")
  private Integer id;

  @Column(name = "invoice_id", insertable = false, updatable = false)
  private Integer invoiceId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invoice_id")
  private Invoice invoice;

  @Column(name = "org_id", insertable = false, updatable = false)
  private Integer orgId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "org_id")
  private Organization organization;

  @Column(nullable = false)
  private Double amount;

  @Column(nullable = false)
  private String currency = "THB";

  @Column(name = "payment_method")
  private String paymentMethod;

  @Column(name = "payment_status")
  private String paymentStatus;

  @Column(name = "paid_at")
  private Instant paidAt;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;
}
