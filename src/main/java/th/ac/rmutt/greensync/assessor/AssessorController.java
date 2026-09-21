package th.ac.rmutt.greensync.assessor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.assessments.Assessment;
import th.ac.rmutt.greensync.assessor.dto.ApproveAssessmentRequest;
import th.ac.rmutt.greensync.assessor.dto.RequestRevisionRequest;
import th.ac.rmutt.greensync.assessor.dto.SaveEvidenceReviewRequest;
import th.ac.rmutt.greensync.assessor.dto.UpdateCertificateRequest;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

@RestController
@RequestMapping("/assessor")
public class AssessorController {

  private static final Set<String> ADMIN_LIKE_ROLES = Set.of("SYSTEM_ADMIN", "ADMIN");

  private final AssessorService assessorService;

  public AssessorController(AssessorService assessorService) {
    this.assessorService = assessorService;
  }

  @GetMapping("/dashboard")
  @PreAuthorize("hasAnyRole('ASSESSOR','SYSTEM_ADMIN','ADMIN')")
  public Map<String, Object> getDashboard(@AuthenticationPrincipal AuthenticatedUser me) {
    return assessorService.getDashboard(me.userId());
  }

  @GetMapping("/assignments")
  @PreAuthorize("hasAnyRole('ASSESSOR','SYSTEM_ADMIN','ADMIN')")
  public List<Map<String, Object>> getAssignments(@AuthenticationPrincipal AuthenticatedUser me) {
    return assessorService.getAssignments(me.userId());
  }

  @GetMapping("/history")
  @PreAuthorize("hasAnyRole('ASSESSOR','SYSTEM_ADMIN','ADMIN')")
  public List<Map<String, Object>> getHistory(@AuthenticationPrincipal AuthenticatedUser me) {
    return assessorService.getHistory(me.userId());
  }

  @GetMapping("/payouts")
  @PreAuthorize("hasRole('ASSESSOR')")
  public List<Map<String, Object>> getPayouts(@AuthenticationPrincipal AuthenticatedUser me) {
    return assessorService.getPayouts(me.userId());
  }

  @GetMapping("/calendar")
  @PreAuthorize("hasRole('ASSESSOR')")
  public List<Map<String, Object>> getCalendar(@AuthenticationPrincipal AuthenticatedUser me) {
    return assessorService.getCalendar(me.userId());
  }

  @GetMapping("/certificates/{id}/pdf")
  @PreAuthorize("hasAnyRole('ASSESSOR','SYSTEM_ADMIN','ADMIN','ORG_ADMIN')")
  public byte[] getCertificatePdf(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    assertAssessmentReadAccess(id, me);
    return assessorService.generateCertificatePdf(id);
  }

  @GetMapping("/organizations/{orgId}/carbon-summary")
  @PreAuthorize("hasAnyRole('ASSESSOR','SYSTEM_ADMIN','ADMIN','ORG_ADMIN','EXECUTIVE','EMPLOYEE','USER')")
  public Map<String, Object> getCarbonSummary(@PathVariable Integer orgId, @AuthenticationPrincipal AuthenticatedUser me) {
    String role = normalizeRole(me.role());
    if (!ADMIN_LIKE_ROLES.contains(role) && !"ASSESSOR".equals(role)) {
      if (!orgId.equals(me.orgId())) {
        throw ApiException.forbidden("ไม่มีสิทธิ์เข้าถึงข้อมูลคาร์บอนขององค์กรอื่น");
      }
    }
    if ("ASSESSOR".equals(role)) {
      assessorService.assertAssessorOrganizationAccess(me.userId(), orgId);
    }
    return assessorService.getOrgCarbonSummary(orgId);
  }

  @GetMapping("/assessments/{id}")
  @PreAuthorize("hasAnyRole('ASSESSOR','SYSTEM_ADMIN','ADMIN','ORG_ADMIN','EXECUTIVE','EMPLOYEE','USER')")
  public Map<String, Object> getAssessment(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    assertAssessmentReadAccess(id, me);
    return assessorService.getAssessmentDetail(id);
  }

  @PostMapping("/assessments/{id}/evidence-review")
  @PreAuthorize("hasAnyRole('ASSESSOR','SYSTEM_ADMIN','ADMIN')")
  public Map<String, Object> saveEvidenceReview(
      @PathVariable Integer id, @RequestBody SaveEvidenceReviewRequest request, @AuthenticationPrincipal AuthenticatedUser me) {
    return assessorService.saveEvidenceReview(id, me.userId(), request);
  }

  @PatchMapping("/assessments/{id}/approve")
  @PreAuthorize("hasAnyRole('ASSESSOR','SYSTEM_ADMIN','ADMIN')")
  public Map<String, Object> approve(
      @PathVariable Integer id, @RequestBody ApproveAssessmentRequest request, @AuthenticationPrincipal AuthenticatedUser me) {
    return assessorService.approveAssessment(id, me.userId(), request);
  }

  @PatchMapping("/assessments/{id}/certificate")
  @PreAuthorize("hasAnyRole('ASSESSOR','SYSTEM_ADMIN','ADMIN')")
  public Map<String, Object> updateCertificate(
      @PathVariable Integer id, @RequestBody UpdateCertificateRequest request, @AuthenticationPrincipal AuthenticatedUser me) {
    return assessorService.updateCertificate(id, me.userId(), request);
  }

  @PatchMapping("/assessments/{id}/request-revision")
  @PreAuthorize("hasAnyRole('ASSESSOR','SYSTEM_ADMIN','ADMIN')")
  public Map<String, Object> requestRevision(
      @PathVariable Integer id, @RequestBody RequestRevisionRequest request, @AuthenticationPrincipal AuthenticatedUser me) {
    return assessorService.requestRevision(id, me.userId(), request);
  }

  private void assertAssessmentReadAccess(Integer assessmentId, AuthenticatedUser me) {
    Assessment assessment = assessorService.findEntity(assessmentId);
    String role = normalizeRole(me.role());
    if (ADMIN_LIKE_ROLES.contains(role)) return;
    if ("ASSESSOR".equals(role)) {
      if (assessment.getAssessor() != null && !assessment.getAssessor().getId().equals(me.userId())) {
        throw ApiException.forbidden("ไม่มีสิทธิ์เข้าถึงการประเมินที่มอบหมายให้ผู้ตรวจคนอื่น");
      }
      return;
    }
    Integer assessmentOrgId = assessment.getOrganization() != null ? assessment.getOrganization().getId() : null;
    if (!java.util.Objects.equals(assessmentOrgId, me.orgId())) {
      throw ApiException.forbidden("ไม่มีสิทธิ์เข้าถึงการประเมินขององค์กรอื่น");
    }
  }

  private String normalizeRole(String role) {
    return role == null ? "" : role.toUpperCase().replaceAll("[\\s_]", "");
  }
}
