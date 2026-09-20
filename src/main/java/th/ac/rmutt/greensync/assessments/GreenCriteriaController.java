package th.ac.rmutt.greensync.assessments;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.assessments.dto.GreenCriteriaRequest;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

@RestController
@RequestMapping("/admin/green-criteria")
public class GreenCriteriaController {

  private final GreenCriteriaService greenCriteriaService;

  public GreenCriteriaController(GreenCriteriaService greenCriteriaService) {
    this.greenCriteriaService = greenCriteriaService;
  }

  private Integer getOrgId(AuthenticatedUser me, HttpServletRequest request) {
    if (!"SYSTEM_ADMIN".equals(me.role())) {
      if (me.orgId() == null) throw ApiException.badRequest("ไม่พบข้อมูลองค์กรในบัญชีผู้ใช้");
      return me.orgId();
    }
    String header = request.getHeader("x-org-id");
    try {
      return header != null ? Integer.parseInt(header) : 0;
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  @GetMapping
  public List<Map<String, Object>> findAll(@AuthenticationPrincipal AuthenticatedUser me, HttpServletRequest request) {
    return greenCriteriaService.findAll(getOrgId(me, request));
  }

  @GetMapping("/list")
  public List<Map<String, Object>> findAllForFrontend(
      @AuthenticationPrincipal AuthenticatedUser me, HttpServletRequest request) {
    return greenCriteriaService.findAllForFrontend(getOrgId(me, request));
  }

  @PostMapping
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public Map<String, Object> create(@RequestBody GreenCriteriaRequest request) {
    return greenCriteriaService.create(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public Map<String, Object> update(@PathVariable Integer id, @RequestBody GreenCriteriaRequest request) {
    return greenCriteriaService.update(id, request);
  }

  @PutMapping("/{id}/score")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','EMPLOYEE','USER')")
  public Map<String, Object> updateScore(
      @PathVariable Integer id,
      @RequestBody Map<String, Double> body,
      @AuthenticationPrincipal AuthenticatedUser me,
      HttpServletRequest request) {
    return greenCriteriaService.updateScore(id, body.get("score"), getOrgId(me, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public void remove(@PathVariable Integer id) {
    greenCriteriaService.remove(id);
  }
}
