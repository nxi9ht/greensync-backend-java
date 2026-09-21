package th.ac.rmutt.greensync.users;

/** Role names as stored in the {@code roles.role_name} column. Mirrors the NestJS UserRole enum exactly. */
public enum UserRole {
  SYSTEM_ADMIN("System Admin"),
  ORG_ADMIN("Organization Admin"),
  EXECUTIVE("Executive"),
  EMPLOYEE("Employee"),
  ASSESSOR("Assessor"),
  USER("User"),
  ADMIN("ADMIN"); // legacy alias

  private final String roleName;

  UserRole(String roleName) {
    this.roleName = roleName;
  }

  public String roleName() {
    return roleName;
  }
}
