package th.ac.rmutt.greensync.executive;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

@RestController
@RequestMapping("/executive")
@PreAuthorize("hasAnyRole('EXECUTIVE','SYSTEM_ADMIN','ORG_ADMIN')")
public class ExecutiveController {

  private final ExecutiveService executiveService;

  public ExecutiveController(ExecutiveService executiveService) {
    this.executiveService = executiveService;
  }

  private Integer orgId(AuthenticatedUser me) {
    if (me.orgId() == null || me.orgId() == 0) {
      throw ApiException.badRequest("บัญชีนี้ไม่ได้เชื่อมกับองค์กร");
    }
    return me.orgId();
  }

  @GetMapping("/dashboard")
  public Map<String, Object> getDashboard(
      @RequestParam(required = false) Integer branchId, @AuthenticationPrincipal AuthenticatedUser me) {
    return executiveService.getDashboard(orgId(me), branchId);
  }

  /** PDF export needs a Thai-font PDF renderer (pdfmake in NestJS) that isn't ported yet. */
  @GetMapping("/export/pdf")
  public void exportDashboardPdf() {
    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "การส่งออก PDF ยังไม่รองรับในเวอร์ชัน Java นี้");
  }

  @PostMapping("/goals")
  public Map<String, Object> setGoal(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthenticatedUser me) {
    Double targetReductionPercent = ((Number) body.get("targetReductionPercent")).doubleValue();
    Integer year = ((Number) body.get("year")).intValue();
    return executiveService.setGoal(orgId(me), targetReductionPercent, year);
  }

  @GetMapping("/leaderboard")
  public List<Map<String, Object>> getLeaderboard(
      @RequestParam(required = false) Integer year, @AuthenticationPrincipal AuthenticatedUser me) {
    return executiveService.getLeaderboard(orgId(me), year);
  }
}
