package th.ac.rmutt.greensync.assessments;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "green_criteria_master")
@Getter
@Setter
@NoArgsConstructor
public class GreenCriteriaMaster {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "criteria_id")
  private Integer id;

  @Column(name = "category_number")
  private Integer categoryNumber;

  @Column(name = "criteria_code")
  private String criteriaCode;

  @Column(name = "criteria_name")
  private String criteriaName;

  @Column(name = "max_score")
  private Double maxScore;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "year_version")
  private Integer yearVersion;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;
}
