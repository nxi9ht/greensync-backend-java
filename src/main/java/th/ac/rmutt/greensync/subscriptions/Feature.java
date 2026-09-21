package th.ac.rmutt.greensync.subscriptions;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "features")
@Getter
@Setter
@NoArgsConstructor
public class Feature {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "feature_id")
  private Integer id;

  @Column(name = "feature_code", unique = true)
  private String featureCode;

  @Column(name = "feature_name")
  private String featureName;

  @Column(columnDefinition = "text")
  private String description;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;
}
