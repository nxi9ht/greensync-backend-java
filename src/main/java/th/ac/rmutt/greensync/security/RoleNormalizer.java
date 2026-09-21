package th.ac.rmutt.greensync.security;

/**
 * Normalizes a raw role string (as stored in {@code roles.role_name}, e.g. "Organization Admin")
 * into the fixed token used everywhere else in the API (e.g. "ORG_ADMIN"). Mirrors the exact
 * normalization performed by the NestJS backend's {@code JwtAuthGuard} so role strings behave
 * identically regardless of which backend issued the token.
 */
public final class RoleNormalizer {

  private RoleNormalizer() {}

  public static String normalize(String role) {
    if (role == null) {
      return null;
    }
    String compact = role.trim().toUpperCase().replaceAll("[\\s_]", "");
    return switch (compact) {
      case "ORGADMIN", "ORGANIZATIONADMIN" -> "ORG_ADMIN";
      // Assessor Admin was merged into System Admin (only 4 roles remain: System Admin,
      // Organization Admin, Assessor, Executive) — any stale token or leftover DB row using
      // this role name still resolves to full system-admin authority rather than breaking.
      case "SYSTEMADMIN", "ADMIN", "ASSESSORADMIN" -> "SYSTEM_ADMIN";
      default -> role.trim().toUpperCase().replace(' ', '_');
    };
  }
}
