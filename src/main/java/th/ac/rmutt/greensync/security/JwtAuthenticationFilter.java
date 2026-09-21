package th.ac.rmutt.greensync.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the Bearer token on every request, and if valid, populates the Spring Security context
 * with an {@link AuthenticatedUser} principal — the equivalent of NestJS's {@code JwtAuthGuard}
 * assigning {@code request.user}. An absent or invalid token simply leaves the request
 * unauthenticated; {@code SecurityConfig} decides which routes require authentication.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      Claims claims = jwtService.parseClaims(token);
      if (claims != null) {
        String subject = claims.getSubject();
        Integer userId = subject != null ? Integer.valueOf(subject) : null;
        String email = claims.get("email", String.class);
        Number orgIdClaim = claims.get("orgId", Number.class);
        Integer orgId = orgIdClaim != null ? orgIdClaim.intValue() : null;
        String role = RoleNormalizer.normalize(claims.get("role", String.class));

        AuthenticatedUser principal = new AuthenticatedUser(userId, email, orgId, role);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

        var authentication =
            new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    }
    filterChain.doFilter(request, response);
  }
}
