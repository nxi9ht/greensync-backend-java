package th.ac.rmutt.greensync.auditlogs;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

/**
 * Unlike the NestJS version (SYSTEM_ADMIN only, which 403'd the main dashboard's "recent
 * activity" widget for every other role), ORG_ADMIN is allowed here too but forced to their own
 * org — the endpoint already accepted an org_id filter, the guard was just stricter than the
 * frontend's actual usage.
 */
@RestController
@RequestMapping("/audit-logs")
public class AuditLogsController {

  private final AuditLogsService auditLogsService;

  public AuditLogsController(AuditLogsService auditLogsService) {
    this.auditLogsService = auditLogsService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public List<Map<String, Object>> findAll(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer limit,
      @RequestParam(name = "org_id", required = false) Integer orgId,
      @AuthenticationPrincipal AuthenticatedUser me) {
    Integer effectiveOrgId = "ORG_ADMIN".equals(me.role()) ? me.orgId() : orgId;
    return auditLogsService.findAll(page, limit, effectiveOrgId);
  }
}
