package th.ac.rmutt.greensync.carbonlogs;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.carbonlogs.dto.CarbonLogRequest;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

@RestController
@RequestMapping("/carbon-logs")
public class CarbonLogsController {

  private static final Logger log = LoggerFactory.getLogger(CarbonLogsController.class);
  private static final List<String> HEADER_ORG_ROLES = List.of("SYSTEM_ADMIN", "ASSESSOR");

  private final CarbonLogsService carbonLogsService;

  public CarbonLogsController(CarbonLogsService carbonLogsService) {
    this.carbonLogsService = carbonLogsService;
  }

  private Integer getOrgId(AuthenticatedUser me, HttpServletRequest request) {
    if (HEADER_ORG_ROLES.contains(me.role())) {
      String header = request.getHeader("x-org-id");
      if (header == null || header.equals("undefined") || header.equals("null")) {
        throw ApiException.badRequest("ไม่พบค่า x-org-id ใน Header");
      }
      try {
        return Integer.parseInt(header);
      } catch (NumberFormatException e) {
        throw ApiException.badRequest("ค่า x-org-id ไม่ถูกต้อง");
      }
    }
    if (me.orgId() == null) {
      throw ApiException.badRequest("ไม่พบไอดีองค์กรของคุณในสิทธิ์การใช้งาน (JWT)");
    }
    return me.orgId();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','USER','EMPLOYEE')")
  public Map<String, Object> create(
      @RequestBody CarbonLogRequest request, @AuthenticationPrincipal AuthenticatedUser me, HttpServletRequest httpRequest) {
    return carbonLogsService.create(request, getOrgId(me, httpRequest));
  }

  @GetMapping("/trend")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public List<Map<String, Object>> getTrend(
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @AuthenticationPrincipal AuthenticatedUser me,
      HttpServletRequest httpRequest) {
    Instant start = startDate != null ? LocalDate.parse(startDate).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    Instant end = endDate != null ? LocalDate.parse(endDate).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    return carbonLogsService.getCarbonTrend(getOrgId(me, httpRequest), start, end);
  }

  @GetMapping("/personal-dashboard")
  @PreAuthorize("hasAnyRole('USER','ORG_ADMIN','EMPLOYEE')")
  public Map<String, Object> getPersonalDashboard(@AuthenticationPrincipal AuthenticatedUser me) {
    return carbonLogsService.getPersonalDashboard(me.userId(), me.orgId());
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','USER','EMPLOYEE','ASSESSOR')")
  public List<Map<String, Object>> findAll(@AuthenticationPrincipal AuthenticatedUser me, HttpServletRequest httpRequest) {
    Integer orgId = getOrgId(me, httpRequest);
    log.info("Executing findAll for orgId: {}", orgId);
    return carbonLogsService.findAll(orgId, null, null);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','USER','EMPLOYEE')")
  public Map<String, Object> update(
      @PathVariable Integer id,
      @RequestBody CarbonLogRequest request,
      @AuthenticationPrincipal AuthenticatedUser me,
      HttpServletRequest httpRequest) {
    return carbonLogsService.update(id, getOrgId(me, httpRequest), request);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','USER','EMPLOYEE')")
  public void remove(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me, HttpServletRequest httpRequest) {
    Integer orgId = getOrgId(me, httpRequest);
    log.info("Executing remove for id: {}, orgId: {}", id, orgId);
    carbonLogsService.remove(id, orgId);
  }
}
