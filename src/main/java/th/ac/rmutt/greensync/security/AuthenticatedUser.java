package th.ac.rmutt.greensync.security;

/**
 * The JWT claims for the current request, set as the Spring Security principal by
 * {@link JwtAuthenticationFilter}. Equivalent to NestJS's {@code request.user}.
 */
public record AuthenticatedUser(Integer userId, String email, Integer orgId, String role) {}
