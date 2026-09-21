package th.ac.rmutt.greensync.analytics;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin-stats")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  public AnalyticsController(AnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  @GetMapping("/dashboard")
  public Map<String, Object> getAdminStats() {
    return analyticsService.getAdminStats();
  }

  @GetMapping("/revenue")
  public Map<String, Object> getRevenueStats() {
    return analyticsService.getRevenueStats();
  }
}
