package th.ac.rmutt.greensync.users;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "bank_accounts")
@Getter
@Setter
@NoArgsConstructor
public class BankAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "bank_account_id")
  private Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "account_name")
  private String accountName;

  @Column(name = "account_no", length = 50)
  private String accountNo;

  @Column(name = "bank_name", length = 100)
  private String bankName;

  @Column(name = "is_primary", nullable = false)
  private boolean primary = false;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;
}
