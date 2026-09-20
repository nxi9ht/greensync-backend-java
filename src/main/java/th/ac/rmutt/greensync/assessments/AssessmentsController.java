package th.ac.rmutt.greensync.assessments;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.assessments.dto.CreateAssessmentRequest;
import th.ac.rmutt.greensync.assessments.dto.UpdateAssessmentRequest;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

@RestController
@RequestMapping("/assessments")
public class AssessmentsController {

  private final AssessmentsService assessmentsService;

  public AssessmentsController(AssessmentsService assessmentsService) {
    this.assessmentsService = assessmentsService;
  }

  private Integer headerOrgId(HttpServletRequest request) {
    String header = request.getHeader("x-org-id");
    try {
      return header != null ? Integer.parseInt(header) : 0;
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public Map<String, Object> create(
      @RequestBody CreateAssessmentRequest request,
      @AuthenticationPrincipal AuthenticatedUser me,
      HttpServletRequest httpRequest) {
    Integer orgId = "SYSTEM_ADMIN".equals(me.role()) ? headerOrgId(httpRequest) : me.orgId();
    return assessmentsService.create(request, orgId);
  }

  @GetMapping("/draft")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public Map<String, Object> getDraft(@AuthenticationPrincipal AuthenticatedUser me) {
    return assessmentsService.getDraft(me.orgId());
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','ASSESSOR','ASSESSOR_ADMIN')")
  public List<Map<String, Object>> findAll(
      @AuthenticationPrincipal AuthenticatedUser me, HttpServletRequest httpRequest) {
    Integer orgId = me.orgId();
    if ("SYSTEM_ADMIN".equals(me.role()) || "ASSESSOR".equals(me.role()) || "ASSESSOR_ADMIN".equals(me.role())) {
      orgId = headerOrgId(httpRequest);
    }
    Integer assessorId = "ASSESSOR".equals(me.role()) ? me.userId() : null;
    return assessmentsService.findAll(orgId, me.role(), assessorId);
  }

  private static final List<String> PRIVILEGED_ROLES = List.of("ASSESSOR", "ASSESSOR_ADMIN", "ADMIN", "SYSTEM_ADMIN");

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','ASSESSOR','ASSESSOR_ADMIN')")
  public Map<String, Object> findOne(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    Integer orgId = PRIVILEGED_ROLES.contains(me.role()) ? 0 : me.orgId();
    Assessment assessment = assessmentsService.findEntity(id, orgId);
    if ("ASSESSOR".equals(me.role())
        && (assessment.getAssessor() == null || !assessment.getAssessor().getId().equals(me.userId()))) {
      throw ApiException.forbidden("ไม่มีสิทธิ์เข้าถึงการประเมินนี้");
    }
    return assessmentsService.findOne(id, orgId);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','ASSESSOR','ASSESSOR_ADMIN')")
  public Map<String, Object> update(
      @PathVariable Integer id,
      @RequestBody UpdateAssessmentRequest request,
      @AuthenticationPrincipal AuthenticatedUser me) {
    Integer orgId = PRIVILEGED_ROLES.contains(me.role()) ? 0 : me.orgId();
    if ("ASSESSOR".equals(me.role())) {
      Assessment assessment = assessmentsService.findEntity(id, 0);
      if (assessment.getAssessor() == null || !assessment.getAssessor().getId().equals(me.userId())) {
        throw ApiException.forbidden("ไม่มีสิทธิ์แก้ไขการประเมินนี้");
      }
    }
    return assessmentsService.update(id, request, orgId);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public void remove(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    Integer orgId = PRIVILEGED_ROLES.contains(me.role()) ? 0 : me.orgId();
    assessmentsService.remove(id, orgId);
  }
}
