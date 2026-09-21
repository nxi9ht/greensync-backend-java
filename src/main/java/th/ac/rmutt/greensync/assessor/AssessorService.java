package th.ac.rmutt.greensync.assessor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.assessments.Assessment;
import th.ac.rmutt.greensync.assessments.AssessmentDetail;
import th.ac.rmutt.greensync.assessments.AssessmentDetailRepository;
import th.ac.rmutt.greensync.assessments.AssessmentMapper;
import th.ac.rmutt.greensync.assessments.AssessmentRepository;
import th.ac.rmutt.greensync.assessments.Certificate;
import th.ac.rmutt.greensync.assessments.CertificateRepository;
import th.ac.rmutt.greensync.assessor.dto.ApproveAssessmentRequest;
import th.ac.rmutt.greensync.assessor.dto.RequestRevisionRequest;
import th.ac.rmutt.greensync.assessor.dto.SaveEvidenceReviewRequest;
import th.ac.rmutt.greensync.assessor.dto.UpdateCertificateRequest;
import th.ac.rmutt.greensync.carbonlogs.CarbonLog;
import th.ac.rmutt.greensync.carbonlogs.CarbonLogRepository;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.organizations.Organization;
import th.ac.rmutt.greensync.users.User;
import th.ac.rmutt.greensync.users.UserRepository;

@Service
public class AssessorService {

  private static final Logger log = LoggerFactory.getLogger(AssessorService.class);

  private static final Map<Integer, String> SCOPE_LABELS =
      Map.of(
          1, "Scope 1 — ก๊าซเรือนกระจกโดยตรง",
          2, "Scope 2 — พลังงานที่ซื้อมา",
          3, "Scope 3 — ก๊าซเรือนกระจกทางอ้อม");

  private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "SUBMITTED", "IN_REVIEW", "REVISION_REQUESTED");
  private static final List<String> COMPLETED_STATUSES = List.of("APPROVED", "REJECTED");
  private static final List<String> UNASSIGNED_POOL_STATUSES = List.of("PENDING", "SUBMITTED", "IN_REVIEW");

  private final AssessmentRepository assessmentRepository;
  private final AssessmentDetailRepository assessmentDetailRepository;
  private final CarbonLogRepository carbonLogRepository;
  private final CertificateRepository certificateRepository;
  private final UserRepository userRepository;
  private final AssessmentMapper assessmentMapper;

  public AssessorService(
      AssessmentRepository assessmentRepository,
      AssessmentDetailRepository assessmentDetailRepository,
      CarbonLogRepository carbonLogRepository,
      CertificateRepository certificateRepository,
      UserRepository userRepository,
      AssessmentMapper assessmentMapper) {
    this.assessmentRepository = assessmentRepository;
    this.assessmentDetailRepository = assessmentDetailRepository;
    this.carbonLogRepository = carbonLogRepository;
    this.certificateRepository = certificateRepository;
    this.userRepository = userRepository;
    this.assessmentMapper = assessmentMapper;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getDashboard(Integer assessorUserId) {
    List<Assessment> assessments = loadAssessmentsForAssessor(assessorUserId);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("stats", buildStats(assessments));
    result.put("assignments", buildAssignments(assessments));
    return result;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> getHistory(Integer assessorUserId) {
    List<Assessment> assessments =
        assessmentRepository.findTop100ByAssessorIdAndStatusInOrderByUpdatedAtDesc(assessorUserId, COMPLETED_STATUSES);
    return buildAssignments(assessments);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> getAssignments(Integer assessorUserId) {
    return buildAssignments(loadAssessmentsForAssessor(assessorUserId));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getOrgCarbonSummary(Integer orgId) {
    return computeCarbonSummary(orgId, null);
  }

  @Transactional(readOnly = true)
  public void assertAssessorOrganizationAccess(Integer assessorUserId, Integer orgId) {
    if (!assessmentRepository.existsByAssessorIdAndOrganizationId(assessorUserId, orgId)) {
      throw ApiException.forbidden("ไม่มีสิทธิ์เข้าถึงข้อมูลองค์กรที่ไม่ได้รับมอบหมาย");
    }
  }

  @Transactional(readOnly = true)
  public Assessment findEntity(Integer assessmentId) {
    return assessmentRepository.findById(assessmentId).orElseThrow(() -> ApiException.notFound("ไม่พบคำขอประเมิน"));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getAssessmentDetail(Integer assessmentId) {
    return assessmentMapper.toDetail(findEntity(assessmentId));
  }

  @Transactional
  public Map<String, Object> saveEvidenceReview(Integer assessmentId, Integer assessorUserId, SaveEvidenceReviewRequest req) {
    if (req.details == null || req.details.isEmpty()) {
      throw ApiException.badRequest("ไม่มีรายการหลักฐานที่ต้องบันทึก");
    }

    Assessment assessment = findEntity(assessmentId);
    assignAssessorIfNeeded(assessment, assessorUserId);

    for (SaveEvidenceReviewRequest.Detail item : req.details) {
      assessment.getDetails().stream()
          .filter(d -> d.getId().equals(item.assessment_detail_id))
          .findFirst()
          .ifPresent(
              detail -> {
                double maxScore = detail.getCriteria() != null && detail.getCriteria().getMaxScore() != null
                    ? detail.getCriteria().getMaxScore()
                    : 5;
                double score =
                    item.assessor_score != null ? item.assessor_score : ("PASS".equals(item.result) ? maxScore : 0);
                detail.setAssessorScore(score);
                if (item.auditor_comment != null) detail.setAuditorComment(item.auditor_comment);
                assessmentDetailRepository.save(detail);
              });
    }

    assessment.setStatus("IN_REVIEW");
    assessmentRepository.save(assessment);
    return getAssessmentDetail(assessmentId);
  }

  @Transactional
  public Map<String, Object> approveAssessment(Integer assessmentId, Integer assessorUserId, ApproveAssessmentRequest req) {
    Assessment assessment = findEntity(assessmentId);
    if (COMPLETED_STATUSES.contains(assessment.getStatus())) {
      throw ApiException.badRequest("คำขอนี้ปิดการประเมินแล้ว");
    }
    assignAssessorIfNeeded(assessment, assessorUserId);

    if (req.details != null) {
      applyDetailUpdates(assessment, req.details);
    }

    double totalScore = req.total_score != null ? req.total_score : calculateTotalScore(assessment);
    String certifiedLevel = req.certified_level != null ? req.certified_level : resolveCertificationLevel(assessment);

    assessment.setStatus("APPROVED");
    assessment.setTotalScore(totalScore);
    assessment.setCertifiedLevel(certifiedLevel);
    if (req.notes != null) assessment.setNotes(req.notes);
    if (assessment.getSubmittedAt() == null) assessment.setSubmittedAt(Instant.now());
    assessmentRepository.save(assessment);

    if (req.certificate_no != null || req.certificate_url != null || req.issued_at != null || req.expired_at != null) {
      upsertCertificate(assessment, req.certificate_no, req.issued_at, req.expired_at, req.certificate_url);
    }

    return getAssessmentDetail(assessmentId);
  }

  @Transactional
  public Map<String, Object> updateCertificate(Integer assessmentId, Integer assessorUserId, UpdateCertificateRequest req) {
    Assessment assessment = findEntity(assessmentId);
    assignAssessorIfNeeded(assessment, assessorUserId);
    upsertCertificate(assessment, req.certificate_no, req.issued_at, req.expired_at, req.certificate_url);
    return getAssessmentDetail(assessmentId);
  }

  private void upsertCertificate(Assessment assessment, String certificateNo, String issuedAt, String expiredAt, String certificateUrl) {
    Certificate cert =
        certificateRepository
            .findByAssessmentId(assessment.getId())
            .orElseGet(
                () -> {
                  Certificate c = new Certificate();
                  c.setAssessment(assessment);
                  c.setOrganization(assessment.getOrganization());
                  return c;
                });
    if (certificateNo != null) cert.setCertificateNo(certificateNo);
    cert.setIssuedAt(issuedAt != null ? Instant.parse(issuedAt) : (cert.getIssuedAt() != null ? cert.getIssuedAt() : Instant.now()));
    if (expiredAt != null) cert.setExpiredAt(Instant.parse(expiredAt));
    if (certificateUrl != null) cert.setCertificateUrl(certificateUrl);
    certificateRepository.save(cert);
  }

  @Transactional
  public Map<String, Object> requestRevision(Integer assessmentId, Integer assessorUserId, RequestRevisionRequest req) {
    if (req.notes == null || req.notes.isBlank()) {
      throw ApiException.badRequest("กรุณาระบุเหตุผลในการส่งกลับแก้ไข");
    }
    Assessment assessment = findEntity(assessmentId);
    if (COMPLETED_STATUSES.contains(assessment.getStatus())) {
      throw ApiException.badRequest("คำขอนี้ปิดการประเมินแล้ว");
    }
    assignAssessorIfNeeded(assessment, assessorUserId);

    if (req.details != null) {
      for (RequestRevisionRequest.Detail item : req.details) {
        assessment.getDetails().stream()
            .filter(d -> d.getId().equals(item.assessment_detail_id))
            .findFirst()
            .ifPresent(
                detail -> {
                  if (item.auditor_comment != null) {
                    detail.setAuditorComment(item.auditor_comment);
                    assessmentDetailRepository.save(detail);
                  }
                });
      }
    }

    assessment.setStatus("REVISION_REQUESTED");
    assessment.setNotes(req.notes);
    assessmentRepository.save(assessment);
    return getAssessmentDetail(assessmentId);
  }

  private List<Assessment> loadAssessmentsForAssessor(Integer assessorUserId) {
    return assessmentRepository.findAssignablePool(assessorUserId, ACTIVE_STATUSES, UNASSIGNED_POOL_STATUSES);
  }

  private Map<String, Object> buildStats(List<Assessment> assessments) {
    long pending = assessments.stream().filter(a -> List.of("PENDING", "SUBMITTED").contains(a.getStatus())).count();
    long inReview = assessments.stream().filter(a -> "IN_REVIEW".equals(a.getStatus())).count();
    long revisionRequested = assessments.stream().filter(a -> "REVISION_REQUESTED".equals(a.getStatus())).count();

    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("pending", pending);
    stats.put("inReview", inReview);
    stats.put("revisionRequested", revisionRequested);
    stats.put("completed", 0);
    stats.put("nearDeadline", countNearDeadline(assessments));
    stats.put("avgScorePercent", avgScorePercent(assessments));
    return stats;
  }

  private List<Map<String, Object>> buildAssignments(List<Assessment> assessments) {
    Map<Integer, String> organizations = new LinkedHashMap<>();
    for (Assessment a : assessments) {
      if (a.getOrganization() != null) {
        organizations.put(a.getOrganization().getId(), a.getOrganization().getName());
      }
    }
    Map<Integer, Map<String, Object>> carbonSummaries = computeCarbonSummaries(organizations);

    return assessments.stream()
        .filter(a -> a.getOrganization() != null && carbonSummaries.containsKey(a.getOrganization().getId()))
        .map(
            a -> {
              Integer orgId = a.getOrganization().getId();
              Map<String, Object> item = new LinkedHashMap<>();
              item.put("id", a.getId());
              item.put("orgId", orgId);
              item.put("orgName", a.getOrganization().getName());
              item.put("assessmentYear", a.getAssessmentYear() != null ? a.getAssessmentYear() : java.time.Year.now().getValue());
              item.put("status", a.getStatus());
              item.put("totalScore", a.getTotalScore() != null ? a.getTotalScore() : 0);
              item.put("submittedAt", a.getSubmittedAt());
              item.put("carbonSummary", carbonSummaries.get(orgId));
              item.put(
                  "hasCertificate",
                  a.getCertificates().stream().anyMatch(c -> c.getCertificateUrl() != null || c.getCertificateNo() != null));
              return item;
            })
        .toList();
  }

  private Map<String, Object> emptyCarbonSummary(Integer orgId, String orgName) {
    List<Map<String, Object>> scopes =
        List.of(1, 2, 3).stream()
            .map(
                scope -> {
                  Map<String, Object> s = new LinkedHashMap<>();
                  s.put("scope", scope);
                  s.put("label", SCOPE_LABELS.get(scope));
                  s.put("totalEmission", 0);
                  s.put("logCount", 0);
                  return s;
                })
            .toList();
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("orgId", orgId);
    summary.put("orgName", orgName);
    summary.put("scopes", new java.util.ArrayList<>(scopes));
    summary.put("totalEmission", 0.0);
    return summary;
  }

  private Map<Integer, Map<String, Object>> computeCarbonSummaries(Map<Integer, String> organizations) {
    Map<Integer, Map<String, Object>> summaries = new LinkedHashMap<>();
    organizations.forEach((id, name) -> summaries.put(id, emptyCarbonSummary(id, name)));
    if (organizations.isEmpty()) return summaries;

    List<CarbonLog> logs = carbonLogRepository.findByOrganizationIdIn(new java.util.ArrayList<>(organizations.keySet()));
    for (CarbonLog logEntry : logs) {
      if (logEntry.getOrganization() == null || logEntry.getEmissionFactor() == null) continue;
      Integer orgId = logEntry.getOrganization().getId();
      Integer scope = logEntry.getEmissionFactor().getScope();
      Map<String, Object> summary = summaries.get(orgId);
      if (summary == null || scope == null || scope < 1 || scope > 3) continue;

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> scopes = (List<Map<String, Object>>) summary.get("scopes");
      Map<String, Object> scopeEntry = scopes.stream().filter(s -> s.get("scope").equals(scope)).findFirst().orElse(null);
      if (scopeEntry == null) continue;

      double emission = logEntry.getTotalEmission() != null ? logEntry.getTotalEmission() : 0;
      double newTotal = ((Number) scopeEntry.get("totalEmission")).doubleValue() + emission;
      int newCount = ((Number) scopeEntry.get("logCount")).intValue() + 1;
      scopeEntry.put("totalEmission", newTotal);
      scopeEntry.put("logCount", newCount);
      summary.put("totalEmission", ((Number) summary.get("totalEmission")).doubleValue() + emission);
    }
    return summaries;
  }

  private Map<String, Object> computeCarbonSummary(Integer orgId, String orgName) {
    Map<String, Object> summary = emptyCarbonSummary(orgId, orgName != null ? orgName : ("องค์กร #" + orgId));
    List<CarbonLog> logs = carbonLogRepository.findByOrganizationIdOrderByCreatedAtAsc(orgId);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> scopes = (List<Map<String, Object>>) summary.get("scopes");
    double total = 0;
    for (CarbonLog logEntry : logs) {
      Integer scope = logEntry.getEmissionFactor() != null ? logEntry.getEmissionFactor().getScope() : null;
      if (scope == null || scope < 1 || scope > 3) continue;
      Map<String, Object> scopeEntry = scopes.stream().filter(s -> s.get("scope").equals(scope)).findFirst().orElse(null);
      if (scopeEntry == null) continue;
      double emission = logEntry.getTotalEmission() != null ? logEntry.getTotalEmission() : 0;
      scopeEntry.put("totalEmission", ((Number) scopeEntry.get("totalEmission")).doubleValue() + emission);
      scopeEntry.put("logCount", ((Number) scopeEntry.get("logCount")).intValue() + 1);
      total += emission;
    }
    summary.put("totalEmission", total);
    return summary;
  }

  private long countNearDeadline(List<Assessment> assessments) {
    Instant threshold = Instant.now().minus(14, ChronoUnit.DAYS);
    return assessments.stream()
        .filter(a -> ACTIVE_STATUSES.contains(a.getStatus()))
        .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isBefore(threshold))
        .count();
  }

  private double avgScorePercent(List<Assessment> assessments) {
    List<Assessment> scored = assessments.stream().filter(a -> a.getTotalScore() != null && a.getTotalScore() > 0).toList();
    if (scored.isEmpty()) return 0;
    double sum = scored.stream().mapToDouble(Assessment::getTotalScore).sum();
    return Math.round(sum / scored.size() * 10) / 10.0;
  }

  private void assignAssessorIfNeeded(Assessment assessment, Integer assessorUserId) {
    if (assessment.getAssessor() != null && !assessment.getAssessor().getId().equals(assessorUserId)) {
      throw ApiException.forbidden("การประเมินนี้มอบหมายให้ผู้ตรวจประเมินคนอื่นแล้ว");
    }
    if (assessment.getAssessor() == null) {
      User assessor = userRepository.findById(assessorUserId).orElseThrow(() -> ApiException.notFound("ไม่พบผู้ใช้งานในระบบ"));
      assessment.setAssessor(assessor);
      assessmentRepository.save(assessment);
    }
  }

  private void applyDetailUpdates(Assessment assessment, List<ApproveAssessmentRequest.Detail> details) {
    for (ApproveAssessmentRequest.Detail item : details) {
      assessment.getDetails().stream()
          .filter(d -> d.getId().equals(item.assessment_detail_id))
          .findFirst()
          .ifPresent(
              detail -> {
                if (item.assessor_score != null) detail.setAssessorScore(item.assessor_score);
                if (item.auditor_comment != null) detail.setAuditorComment(item.auditor_comment);
                assessmentDetailRepository.save(detail);
              });
    }
  }

  private double calculateTotalScore(Assessment assessment) {
    return assessment.getDetails().stream()
        .mapToDouble(d -> d.getAssessorScore() != null ? d.getAssessorScore() : 0)
        .sum();
  }

  private String resolveCertificationLevel(Assessment assessment) {
    double max =
        assessment.getDetails().stream()
            .mapToDouble(d -> d.getCriteria() != null && d.getCriteria().getMaxScore() != null ? d.getCriteria().getMaxScore() : 5)
            .sum();
    double total = calculateTotalScore(assessment);
    double percent = max > 0 ? total / max * 100 : 0;
    if (percent >= 90) return "ระดับ ทอง (Gold)";
    if (percent >= 80) return "ระดับ เงิน (Silver)";
    if (percent >= 60) return "ระดับ ทองแดง (Bronze)";
    return "ไม่ผ่านการรับรอง";
  }

  /** Stripe payouts depend on the subscriptions module's StripeService, not ported yet. */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> getPayouts(Integer assessorUserId) {
    log.debug("getPayouts called for assessor {} — Stripe integration not ported, returning empty list", assessorUserId);
    return List.of();
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> getCalendar(Integer assessorUserId) {
    List<Assessment> assigned = assessmentRepository.findByAssessorIdAndStatusIn(assessorUserId, ACTIVE_STATUSES);
    List<Assessment> unassigned = assessmentRepository.findByAssessorIsNullAndStatusIn(List.of("PENDING", "SUBMITTED"));

    List<Map<String, Object>> events = new java.util.ArrayList<>();
    for (Assessment a : assigned) {
      events.add(calendarEvent(a, true));
    }
    for (Assessment a : unassigned) {
      events.add(calendarEvent(a, false));
    }
    return events;
  }

  private Map<String, Object> calendarEvent(Assessment a, boolean isAssigned) {
    String orgName = a.getOrganization() != null ? a.getOrganization().getName() : "องค์กร";
    String prefix = isAssigned ? "" : "[ยังไม่รับงาน] ";
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("id", a.getId());
    event.put("title", prefix + "ตรวจประเมิน: " + orgName);
    event.put("date", a.getSubmittedAt() != null ? a.getSubmittedAt() : a.getCreatedAt());
    event.put("status", a.getStatus());
    event.put("isAssigned", isAssigned);
    return event;
  }

  /** Thai-font PDF generation (pdfmake in NestJS) isn't ported yet. */
  public byte[] generateCertificatePdf(Integer assessmentId) {
    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "การสร้าง PDF ใบรับรองยังไม่รองรับในเวอร์ชัน Java นี้");
  }
}
