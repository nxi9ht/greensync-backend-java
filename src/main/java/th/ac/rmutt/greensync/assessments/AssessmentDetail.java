package th.ac.rmutt.greensync.assessments;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "assessment_details")
@Getter
@Setter
@NoArgsConstructor
public class AssessmentDetail {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_detail_id")
  private Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assessment_id")
  private Assessment assessment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "criteria_id")
  private GreenCriteriaMaster criteria;

  @Column(name = "self_score", nullable = false)
  private Double selfScore = 0.0;

  @Column(name = "applicant_comment", columnDefinition = "text")
  private String applicantComment;

  @Column(name = "assessor_score", nullable = false)
  private Double assessorScore = 0.0;

  @Column(name = "auditor_comment", columnDefinition = "text")
  private String auditorComment;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at")
  private Instant updatedAt = Instant.now();

  @OneToMany(mappedBy = "assessmentDetail", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<EvidenceFile> evidenceFiles = new ArrayList<>();
}
