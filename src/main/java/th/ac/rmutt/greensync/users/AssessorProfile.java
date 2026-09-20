package th.ac.rmutt.greensync.users;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "assessor_profiles")
@Getter
@Setter
@NoArgsConstructor
public class AssessorProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessor_profile_id")
  private Integer id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "license_number", length = 100)
  private String licenseNumber;

  @Column(name = "years_experience")
  private Integer yearsExperience;

  @Column(name = "education_background", columnDefinition = "text")
  private String educationBackground;

  @Column(name = "qualification_file_url")
  private String qualificationFileUrl;

  /** "Pending" | "Verified" | "Rejected" */
  @Column(name = "verification_status", length = 50)
  private String verificationStatus;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "verified_by")
  private User verifiedBy;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;
}
