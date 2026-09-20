package th.ac.rmutt.greensync.users;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import th.ac.rmutt.greensync.organizations.Organization;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "user_id")
  private Integer id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "password_setup_required", nullable = false)
  private boolean passwordSetupRequired = false;

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private Instant updatedAt;

  @Column(name = "reset_password_token")
  private String resetPasswordToken;

  @Column(name = "reset_password_expires")
  private Instant resetPasswordExpires;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "org_id")
  private Organization organization;

  @Column(name = "org_unit_id")
  private Integer orgUnitId;

  @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  private UserProfile userProfile;

  @OneToOne(mappedBy = "user")
  private AssessorProfile assessorProfile;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<Role> roles = new HashSet<>();
}
