package th.ac.rmutt.greensync.assessoradmin;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.assessments.Assessment;
import th.ac.rmutt.greensync.assessments.AssessmentRepository;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.users.AssessorProfileRepository;
import th.ac.rmutt.greensync.users.BankAccount;
import th.ac.rmutt.greensync.users.BankAccountRepository;
import th.ac.rmutt.greensync.users.UserRepository;

@Service
public class AssessorAdminService {

  private static final Set<String> COMPLETED_STATUSES = Set.of("APPROVED", "REJECTED");
  private static final Set<String> PENDING_STATUSES = Set.of("PENDING", "SUBMITTED");
  private static final Set<String> IN_PROGRESS_STATUSES = Set.of("IN_REVIEW", "REVISION_REQUESTED");

  private final UserRepository userRepository;
  private final AssessmentRepository assessmentRepository;
  private final AssessorProfileRepository assessorProfileRepository;
  private final BankAccountRepository bankAccountRepository;

  public AssessorAdminService(
      UserRepository userRepository,
      AssessmentRepository assessmentRepository,
      AssessorProfileRepository assessorProfileRepository,
      BankAccountRepository bankAccountRepository) {
    this.userRepository = userRepository;
    this.assessmentRepository = assessmentRepository;
    this.assessorProfileRepository = assessorProfileRepository;
    this.bankAccountRepository = bankAccountRepository;
  }

  @Transactional
  public Map<String, Object> assignAssessor(Integer assessmentId, Integer assessorId) {
    Assessment assessment =
        assessmentRepository.findById(assessmentId).orElseThrow(() -> ApiException.notFound("Assessment not found"));

    var assessor = userRepository.findById(assessorId).orElseThrow(() -> ApiException.notFound("Assessor not found"));
    assessment.setAssessor(assessor);
    if ("SUBMITTED".equals(assessment.getStatus())) {
      assessment.setStatus("IN_REVIEW");
    }
    assessmentRepository.save(assessment);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("success", true);
    result.put("message", "Assigned successfully");
    result.put("assessmentId", assessmentId);
    result.put("assessorId", assessorId);
    return result;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getAssessorPerformance(Integer assessorId) {
    List<Assessment> assessments = assessmentRepository.findAll().stream()
        .filter(a -> a.getAssessor() != null && a.getAssessor().getId().equals(assessorId))
        .toList();

    long completed = assessments.stream().filter(a -> COMPLETED_STATUSES.contains(a.getStatus())).count();
    long pending = assessments.stream().filter(a -> IN_PROGRESS_STATUSES.contains(a.getStatus())).count();
    long approved = assessments.stream().filter(a -> "APPROVED".equals(a.getStatus())).count();

    double approvalRate = completed > 0 ? ((double) approved / completed) * 100 : 0;

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("assessorId", assessorId);
    result.put("completed", completed);
    result.put("pending", pending);
    result.put("approvalRate", Math.round(approvalRate * 10) / 10.0);
    return result;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getDashboardStats() {
    long totalAssessors =
        userRepository.findAll().stream()
            .filter(u -> u.isActive())
            .filter(
                u ->
                    u.getRoles().stream()
                        .anyMatch(
                            r ->
                                "ASSESSOR".equalsIgnoreCase(r.getRoleName())
                                    || "ASSESSOR ADMIN".equalsIgnoreCase(r.getRoleName())
                                    || "ASSESSOR_ADMIN".equalsIgnoreCase(r.getRoleName())))
            .count();

    List<Assessment> allAssessments = assessmentRepository.findAll();
    List<Assessment> assigned = allAssessments.stream().filter(a -> a.getAssessor() != null).toList();
    long unassigned =
        allAssessments.stream()
            .filter(a -> a.getAssessor() == null && PENDING_STATUSES.contains(a.getStatus()))
            .count();
    long inReview = allAssessments.stream().filter(a -> "IN_REVIEW".equals(a.getStatus())).count();
    long completed = allAssessments.stream().filter(a -> COMPLETED_STATUSES.contains(a.getStatus())).count();
    long approved = allAssessments.stream().filter(a -> "APPROVED".equals(a.getStatus())).count();
    double globalApprovalRate = completed > 0 ? Math.round(((double) approved / completed) * 100 * 10) / 10.0 : 0;

    Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
    long recentAssignments =
        assigned.stream()
            .filter(a -> a.getUpdatedAt() != null && !a.getUpdatedAt().isBefore(thirtyDaysAgo))
            .count();

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalAssessors", totalAssessors);
    result.put("assigned", assigned.size());
    result.put("unassigned", unassigned);
    result.put("inReview", inReview);
    result.put("completed", completed);
    result.put("approved", approved);
    result.put("globalApprovalRate", globalApprovalRate);
    result.put("recentAssignments", recentAssignments);
    return result;
  }

  /**
   * Stripe payouts depend on the subscriptions module's StripeService, which isn't ported to
   * Java yet — surfacing a clear error here rather than a raw 500 until that module lands.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> processPayout(Integer assessorId, Double amount) {
    var profile =
        assessorProfileRepository
            .findByUserId(assessorId)
            .orElseThrow(() -> ApiException.notFound("Assessor profile not found"));

    BankAccount bankAccount =
        bankAccountRepository.findFirstByUserId(profile.getUser().getId()).orElse(null);
    if (bankAccount == null) {
      throw ApiException.badRequest("Assessor does not have a registered bank account");
    }

    throw new ApiException(
        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
        "การจ่ายเงินผ่าน Stripe ยังไม่รองรับในเวอร์ชัน Java นี้ (subscriptions module ยังไม่ได้พอร์ต)");
  }
}
