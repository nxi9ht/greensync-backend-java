package th.ac.rmutt.greensync.gemini;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import th.ac.rmutt.greensync.users.User;

@Entity
@Table(name = "chat_logs")
@Getter
@Setter
@NoArgsConstructor
public class ChatLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "chat_log_id")
  private Integer id;

  @Column(name = "user_id", insertable = false, updatable = false)
  private Integer userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(columnDefinition = "text")
  private String question;

  @Column(columnDefinition = "text")
  private String answer;

  private String intent;

  @Column(name = "session_id")
  private Integer sessionId;

  @Column(name = "session_title")
  private String sessionTitle;

  @Column(name = "related_module")
  private String relatedModule;

  @Column(name = "confidence_score")
  private Double confidenceScore;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;
}
