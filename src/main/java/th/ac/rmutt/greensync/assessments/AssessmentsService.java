package th.ac.rmutt.greensync.assessments;

import java.time.Year;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.assessments.dto.CreateAssessmentRequest;
import th.ac.rmutt.greensync.assessments.dto.UpdateAssessmentRequest;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.notifications.MailService;
import th.ac.rmutt.greensync.organizations.Organization;
import th.ac.rmutt.greensync.organizations.OrganizationRepository;
import th.ac.rmutt.greensync.users.User;
import th.ac.rmutt.greensync.users.UsersService;

@Service
public class AssessmentsService {

  private static final Logger log = LoggerFactory.getLogger(AssessmentsService.class);
  private static final List<String> REVIEWED_STATUSES = List.of("REVISION_REQUESTED", "APPROVED", "REJECTED");

  private final AssessmentRepository assessmentRepository;
  private final AssessmentDetailRepository assessmentDetailRepository;
  private final GreenCriteriaMasterRepository criteriaRepository;
  private final OrganizationRepository organizationRepository;
  private final AssessmentMapper mapper;
  private final MailService mailService;
  private final UsersService usersService;

  public AssessmentsService(
      AssessmentRepository assessmentRepository,
      AssessmentDetailRepository assessmentDetailRepository,
      GreenCriteriaMasterRepository criteriaRepository,
      OrganizationRepository organizationRepository,
      AssessmentMapper mapper,
      MailService mailService,
      UsersService usersService) {
    this.assessmentRepository = assessmentRepository;
    this.assessmentDetailRepository = assessmentDetailRepository;
    this.criteriaRepository = criteriaRepository;
    this.organizationRepository = organizationRepository;
    this.mapper = mapper;
    this.mailService = mailService;
    this.usersService = usersService;
  }

  @Transactional
  public Map<String, Object> create(CreateAssessmentRequest req, Integer orgId) {
    Organization org = organizationRepository.findById(orgId).orElseThrow(() -> ApiException.notFound("ไม่พบองค์กรในระบบ"));

    Assessment assessment = new Assessment();
    assessment.setOrganization(org);
    assessment.setStatus(req.status != null ? req.status : "PENDING");
    assessment.setAssessmentYear(req.assessment_year != null ? req.assessment_year : Year.now().getValue());
    if (req.total_score != null) assessment.setTotalScore(req.total_score);
    if (req.certified_level != null) assessment.setCertifiedLevel(req.certified_level);
    if (req.notes != null) assessment.setNotes(req.notes);
    assessment = assessmentRepository.save(assessment);

    boolean hasPassed = assessmentRepository.findFirstByOrganizationIdAndStatus(orgId, "APPROVED").isPresent();
    List<GreenCriteriaMaster> criteriaList = criteriaRepository.findAllOrdered();
    if (!hasPassed) {
      criteriaList = criteriaList.stream().filter(c -> !Integer.valueOf(7).equals(c.getCategoryNumber())).toList();
    }

    for (GreenCriteriaMaster criteria : criteriaList) {
      AssessmentDetail detail = new AssessmentDetail();
      detail.setAssessment(assessment);
      detail.setCriteria(criteria);
      assessmentDetailRepository.save(detail);
    }

    return findOne(assessment.getId(), orgId);
  }

  @Transactional
  public Map<String, Object> getDraft(Integer orgId) {
    return assessmentRepository
        .findFirstByOrganizationIdAndStatus(orgId, "DRAFT")
        .map(mapper::toDetail)
        .orElseGet(
            () -> {
              CreateAssessmentRequest req = new CreateAssessmentRequest();
              req.status = "DRAFT";
              return create(req, orgId);
            });
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAll(Integer orgId, String role, Integer assessorId) {
    String normalizedRole = role == null ? "" : role.trim().toUpperCase().replace(' ', '_');

    List<Assessment> assessments;
    if (normalizedRole.equals("ADMIN") || normalizedRole.equals("SYSTEM_ADMIN")) {
      assessments = assessmentRepository.findTop50ByOrderBySubmittedAtDesc();
    } else if (normalizedRole.equals("ASSESSOR")) {
      assessments =
          normalizedRole.equals("ASSESSOR") && assessorId != null
              ? assessmentRepository.findTop50ByAssessorIdOrderBySubmittedAtDesc(assessorId)
              : assessmentRepository.findTop50ByOrderBySubmittedAtDesc();
    } else {
      assessments = assessmentRepository.findTop50ByOrganizationIdOrderBySubmittedAtDesc(orgId);
    }
    return assessments.stream().map(mapper::toListItem).toList();
  }

  @Transactional(readOnly = true)
  public Assessment findEntity(Integer id, Integer orgId) {
    return (orgId == null || orgId == 0
            ? assessmentRepository.findById(id)
            : assessmentRepository.findByIdAndOrganizationId(id, orgId))
        .orElseThrow(() -> ApiException.notFound("Assessment not found"));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> findOne(Integer id, Integer orgId) {
    return mapper.toDetail(findEntity(id, orgId));
  }

  @Transactional
  public Map<String, Object> update(Integer id, UpdateAssessmentRequest req, Integer orgId) {
    Assessment assessment = findEntity(id, orgId);
    String oldStatus = assessment.getStatus();

    if (req.status != null) assessment.setStatus(req.status);
    if (req.total_score != null) assessment.setTotalScore(req.total_score);
    if (req.certified_level != null) assessment.setCertifiedLevel(req.certified_level);
    if (req.assessor_user_id != null) {
      // Caller resolves the assessor id to a User elsewhere if needed; for now assessments
      // are assigned via the assessor-admin module (not yet ported), so this stays a no-op
      // placeholder until that module exists.
      log.debug("assessor_user_id reassignment requested but assessor-admin module isn't ported yet");
    }
    assessmentRepository.save(assessment);

    if (req.details != null && !req.details.isEmpty()) {
      for (UpdateAssessmentRequest.DetailUpdate update : req.details) {
        assessment.getDetails().stream()
            .filter(d -> d.getId().equals(update.assessment_detail_id))
            .findFirst()
            .ifPresent(
                detail -> {
                  if (update.self_score != null) detail.setSelfScore(update.self_score);
                  if (update.applicant_comment != null) detail.setApplicantComment(update.applicant_comment);
                  if (update.assessor_score != null) detail.setAssessorScore(update.assessor_score);
                  if (update.auditor_comment != null) detail.setAuditorComment(update.auditor_comment);
                  assessmentDetailRepository.save(detail);
                });
      }
    }

    // assessor_score is NOT NULL DEFAULT 0 in the schema, so — matching the NestJS service's
    // literal `d.assessor_score !== null && d.assessor_score !== undefined` check exactly —
    // it is always considered "set" and the self_score fallback never actually triggers.
    double newTotalScore =
        assessmentDetailRepository.findByAssessmentId(assessment.getId()).stream()
            .mapToDouble(d -> d.getAssessorScore() != null ? d.getAssessorScore() : 0)
            .sum();
    assessment.setTotalScore(newTotalScore);
    assessmentRepository.save(assessment);

    if (req.status != null && !req.status.equals(oldStatus)) {
      notifyStatusChange(assessment, req.status);
    }

    return findOne(id, orgId);
  }

  private void notifyStatusChange(Assessment assessment, String newStatus) {
    Organization org = assessment.getOrganization();
    usersService
        .findOrgAdmin(org.getId())
        .ifPresent(
            (User admin) -> {
              try {
                if ("SUBMITTED".equals(newStatus)) {
                  mailService.sendMail(
                      admin.getEmail(),
                      "ระบบได้รับข้อมูลการประเมินแล้ว",
                      mailService.assessmentSubmittedTemplate(org.getName()));
                } else if (REVIEWED_STATUSES.contains(newStatus)) {
                  mailService.sendMail(
                      admin.getEmail(),
                      "แจ้งผลการประเมินเบื้องต้น",
                      mailService.assessmentReviewedTemplate(org.getName(), newStatus));
                }
              } catch (Exception e) {
                log.error("Failed to send assessment status email", e);
              }
            });
  }

  @Transactional
  public void remove(Integer id, Integer orgId) {
    Assessment assessment = findEntity(id, orgId);
    assessmentRepository.delete(assessment);
  }
}
