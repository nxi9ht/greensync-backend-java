package th.ac.rmutt.greensync.orgadmin;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.assessments.Assessment;
import th.ac.rmutt.greensync.assessments.AssessmentMapper;
import th.ac.rmutt.greensync.assessments.AssessmentRepository;
import th.ac.rmutt.greensync.carbonlogs.CarbonLogMapper;
import th.ac.rmutt.greensync.carbonlogs.CarbonLogRepository;
import th.ac.rmutt.greensync.common.ApiException;

@Service
public class OrgAdminService {

  private final AssessmentRepository assessmentRepository;
  private final CarbonLogRepository carbonLogRepository;
  private final AssessmentMapper assessmentMapper;
  private final CarbonLogMapper carbonLogMapper;

  public OrgAdminService(
      AssessmentRepository assessmentRepository,
      CarbonLogRepository carbonLogRepository,
      AssessmentMapper assessmentMapper,
      CarbonLogMapper carbonLogMapper) {
    this.assessmentRepository = assessmentRepository;
    this.carbonLogRepository = carbonLogRepository;
    this.assessmentMapper = assessmentMapper;
    this.carbonLogMapper = carbonLogMapper;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getRevisionCenter(Integer orgId) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(
        "revisions",
        assessmentRepository
            .findByOrganizationIdAndStatusOrderByUpdatedAtDesc(orgId, "REVISION_REQUESTED")
            .stream()
            .map(assessmentMapper::toDetail)
            .toList());
    result.put(
        "carbonLogs",
        carbonLogRepository.findByOrganizationIdOrderByYearDescMonthDescCreatedAtDesc(orgId).stream()
            .map(carbonLogMapper::toMap)
            .toList());
    return result;
  }

  @Transactional
  public Map<String, Object> sendToUser(Integer assessmentId, Integer orgId, String notes) {
    if (notes == null || notes.isBlank()) {
      throw ApiException.badRequest("กรุณาระบุข้อความสำหรับส่งกลับผู้ใช้งาน");
    }
    Assessment assessment =
        assessmentRepository
            .findByIdAndOrganizationIdAndStatus(assessmentId, orgId, "REVISION_REQUESTED")
            .orElseThrow(() -> ApiException.notFound("ไม่พบงานที่ตีกลับสำหรับองค์กรนี้"));

    assessment.setNotes(notes.trim());
    assessment.setStatus("DRAFT");
    assessmentRepository.save(assessment);
    return assessmentMapper.toDetail(assessment);
  }

  @Transactional
  public Map<String, Object> resubmitRevision(Integer assessmentId, Integer orgId, String notes) {
    Assessment assessment =
        assessmentRepository
            .findByIdAndOrganizationIdAndStatus(assessmentId, orgId, "REVISION_REQUESTED")
            .orElseThrow(() -> ApiException.notFound("ไม่พบงานที่ตีกลับสำหรับองค์กรนี้"));

    assessment.setStatus("SUBMITTED");
    if (notes != null && !notes.isBlank()) {
      assessment.setNotes(notes.trim());
    }
    assessment.setSubmittedAt(Instant.now());
    assessmentRepository.save(assessment);
    return assessmentMapper.toDetail(assessment);
  }
}
