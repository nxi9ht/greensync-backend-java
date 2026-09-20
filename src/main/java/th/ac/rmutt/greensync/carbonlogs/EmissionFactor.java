package th.ac.rmutt.greensync.carbonlogs;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "emission_factors")
@Getter
@Setter
@NoArgsConstructor
public class EmissionFactor {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "emission_factor_id")
  private Integer id;

  @Column(nullable = false)
  private String name;

  private Integer scope;

  private String unit;

  @Column(name = "factor_value")
  private Double factorValue;

  private Integer year;

  private String source;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;
}
