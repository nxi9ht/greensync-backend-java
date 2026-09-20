package th.ac.rmutt.greensync.assessments;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import th.ac.rmutt.greensync.organizations.Organization;
import th.ac.rmutt.greensync.users.User;

@Entity
@Table(name = "assessments")
@Getter
@Setter
@NoArgsConstructor
public class Assessment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_id")
  private Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "org_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assessor_user_id")
  private User assessor;

  @Column(name = "assessment_year")
  private Integer assessmentYear;

  @Column(nullable = false)
  private String status = "PENDING";

  @Column(name = "total_score", nullable = false)
  private Double totalScore = 0.0;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(name = "certified_level")
  private String certifiedLevel;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;

  @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<AssessmentDetail> details = new ArrayList<>();

  @OneToMany(mappedBy = "assessment")
  private List<Certificate> certificates = new ArrayList<>();
}
