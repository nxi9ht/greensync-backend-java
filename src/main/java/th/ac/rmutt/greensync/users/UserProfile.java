package th.ac.rmutt.greensync.users;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

  @Id
  @Column(name = "user_id")
  private Integer userId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "first_name", length = 100)
  private String firstName;

  @Column(name = "last_name", length = 100)
  private String lastName;

  @Column(length = 20)
  private String phone;

  @Column(name = "profile_image", columnDefinition = "text")
  private String profileImage;

  @Column(name = "personal_goal_percent", precision = 5, scale = 2)
  private BigDecimal personalGoalPercent;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private Instant updatedAt;
}
