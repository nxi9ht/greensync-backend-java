package th.ac.rmutt.greensync.assessments;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import th.ac.rmutt.greensync.users.User;

@Entity
@Table(name = "evidence_files")
@Getter
@Setter
@NoArgsConstructor
public class EvidenceFile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "evidence_file_id")
  private Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assessment_detail_id")
  private AssessmentDetail assessmentDetail;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "uploaded_by_user_id")
  private User uploadedBy;

  @Column(name = "carbon_log_id")
  private Integer carbonLogId;

  @Column(name = "file_name")
  private String fileName;

  @Column(name = "file_url", columnDefinition = "text")
  private String fileUrl;

  @Column(name = "file_type")
  private String fileType;

  @Column(name = "file_size")
  private Long fileSize;

  private String category;

  @CreationTimestamp
  @Column(name = "uploaded_at")
  private Instant uploadedAt;
}
