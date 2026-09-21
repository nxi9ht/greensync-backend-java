package th.ac.rmutt.greensync.auditlogs;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import th.ac.rmutt.greensync.users.User;

@Entity
@Table(name = "assessment_audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "audit_log_id")
  private Integer id;

  @Column(name = "assessment_detail_id")
  private Integer assessmentDetailId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "action_by_user_id")
  private User user;

  private String action;

  @Column(columnDefinition = "text")
  private String comment;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;
}
