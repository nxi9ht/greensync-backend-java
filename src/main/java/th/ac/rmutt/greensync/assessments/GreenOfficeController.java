package th.ac.rmutt.greensync.assessments;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

@RestController
@RequestMapping("/green-office")
public class GreenOfficeController {

  private static final List<String> HEADER_ORG_ROLES = List.of("SYSTEM_ADMIN", "ASSESSOR");

  private final GreenCriteriaService greenCriteriaService;

  public GreenOfficeController(GreenCriteriaService greenCriteriaService) {
    this.greenCriteriaService = greenCriteriaService;
  }

  private Integer getOrgId(AuthenticatedUser me, HttpServletRequest request) {
    if (HEADER_ORG_ROLES.contains(me.role())) {
      String header = request.getHeader("x-org-id");
      Integer orgId;
      try {
        orgId = header != null ? Integer.parseInt(header) : 0;
      } catch (NumberFormatException e) {
        orgId = 0;
      }
      if (orgId == 0) throw ApiException.badRequest("กรุณาระบุองค์กร");
      return orgId;
    }
    if (me.orgId() == null) throw ApiException.badRequest("ไม่พบข้อมูลองค์กรในบัญชีผู้ใช้");
    return me.orgId();
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','USER','ASSESSOR')")
  public List<Map<String, Object>> findAll(@AuthenticationPrincipal AuthenticatedUser me, HttpServletRequest request) {
    return greenCriteriaService.findAllForFrontend(getOrgId(me, request)).stream()
        .map(this::withSnakeCaseAliases)
        .toList();
  }

  @PutMapping("/{id}/score")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public Map<String, Object> updateScore(
      @PathVariable Integer id,
      @RequestBody Map<String, Double> body,
      @AuthenticationPrincipal AuthenticatedUser me,
      HttpServletRequest request) {
    return greenCriteriaService.updateScore(id, body.get("score"), getOrgId(me, request));
  }

  private Map<String, Object> withSnakeCaseAliases(Map<String, Object> item) {
    var withAliases = new java.util.LinkedHashMap<>(item);
    withAliases.put("max_score", item.get("maxScore"));
    withAliases.put("current_score", item.get("currentScore"));
    return withAliases;
  }
}
