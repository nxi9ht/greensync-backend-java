package th.ac.rmutt.greensync.subscriptions;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlan {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "plan_id")
  private Integer id;

  @Column(name = "plan_name", nullable = false)
  private String planName;

  @Column(columnDefinition = "text")
  private String description;

  private String badge;

  @Column(name = "price_per_month")
  private Double pricePerMonth;

  @Column(name = "stripe_price_id")
  private String stripePriceId;

  @Column(name = "max_users")
  private Integer maxUsers;

  @Column(name = "max_locations")
  private Integer maxLocations;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @ManyToMany
  @JoinTable(
      name = "plan_features",
      joinColumns = @JoinColumn(name = "plan_id"),
      inverseJoinColumns = @JoinColumn(name = "feature_id"))
  private List<Feature> features = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;
}
