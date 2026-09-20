package th.ac.rmutt.greensync.organizations;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "organization_units")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationUnit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "unit_id")
  private Integer id;

  @Column(name = "org_id", insertable = false, updatable = false)
  private Integer orgId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "org_id")
  private Organization organization;

  @Column(name = "unit_name", nullable = false)
  private String unitName;

  @Column(name = "parent_unit_id", insertable = false, updatable = false)
  private Integer parentUnitId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_unit_id")
  private OrganizationUnit parentUnit;

  @Column(name = "unit_type")
  private String unitType;

  @Column
  private Double area;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;
}
