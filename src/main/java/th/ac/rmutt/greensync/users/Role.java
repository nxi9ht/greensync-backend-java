package th.ac.rmutt.greensync.users;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "role_id")
  private Integer id;

  @Column(name = "role_name", nullable = false, unique = true, length = 50)
  private String roleName;

  public Role(String roleName) {
    this.roleName = roleName;
  }
}
