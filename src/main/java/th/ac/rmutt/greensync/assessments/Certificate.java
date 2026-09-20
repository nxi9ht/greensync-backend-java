package th.ac.rmutt.greensync.assessments;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import th.ac.rmutt.greensync.organizations.Organization;

@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
public class Certificate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "certificate_id")
  private Integer id;

  @Column(name = "certificate_no", unique = true)
  private String certificateNo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assessment_id")
  private Assessment assessment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "org_id")
  private Organization organization;

  @Column(name = "issued_at")
  private Instant issuedAt;

  @Column(name = "expired_at")
  private Instant expiredAt;

  @Column(name = "certificate_url")
  private String certificateUrl;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;
}
