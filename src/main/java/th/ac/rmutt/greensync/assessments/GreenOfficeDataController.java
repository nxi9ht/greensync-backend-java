package th.ac.rmutt.greensync.assessments;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

@RestController
@RequestMapping("/green-office-data")
public class GreenOfficeDataController {

  private static final List<String> PRIVILEGED_ROLES = List.of("SYSTEM_ADMIN", "ASSESSOR", "ASSESSOR_ADMIN");

  private final GreenCriteriaService greenCriteriaService;

  public GreenOfficeDataController(GreenCriteriaService greenCriteriaService) {
    this.greenCriteriaService = greenCriteriaService;
  }

  @GetMapping
  public List<Map<String, Object>> findAll(@AuthenticationPrincipal AuthenticatedUser me, HttpServletRequest request) {
    Integer orgId;
    if (PRIVILEGED_ROLES.contains(me.role())) {
      String header = request.getHeader("x-org-id");
      try {
        orgId = header != null ? Integer.parseInt(header) : null;
      } catch (NumberFormatException e) {
        orgId = null;
      }
    } else {
      orgId = me.orgId();
    }
    if (orgId == null || orgId == 0) throw ApiException.badRequest("ไม่พบข้อมูลองค์กร");

    return greenCriteriaService.findAllForFrontend(orgId).stream()
        .map(
            item -> {
              Map<String, Object> withAliases = new LinkedHashMap<>(item);
              withAliases.put("max_score", item.get("maxScore"));
              withAliases.put("current_score", item.get("currentScore"));
              return withAliases;
            })
        .toList();
  }
}
