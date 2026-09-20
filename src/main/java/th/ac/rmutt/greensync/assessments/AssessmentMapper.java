package th.ac.rmutt.greensync.assessments;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import th.ac.rmutt.greensync.organizations.OrganizationMapper;
import th.ac.rmutt.greensync.users.User;

/**
 * Flattens the assessment entity graph into the same snake_case shape the NestJS TypeORM
 * entities produced (their TS property names are already snake_case).
 */
@Component
public class AssessmentMapper {

  private final OrganizationMapper organizationMapper;

  public AssessmentMapper(OrganizationMapper organizationMapper) {
    this.organizationMapper = organizationMapper;
  }

  /** Full detail shape: organization + details (with criteria + evidence_files) + certificates. */
  public Map<String, Object> toDetail(Assessment a) {
    Map<String, Object> map = base(a);
    map.put("organization", a.getOrganization() != null ? organizationMapper.toMap(a.getOrganization()) : null);
    map.put("details", a.getDetails().stream().map(this::toDetailItem).toList());
    map.put("certificates", a.getCertificates().stream().map(this::toCertificate).toList());
    return map;
  }

  /** List shape (findAll): organization + assessor (+ profile) + certificates, no details. */
  public Map<String, Object> toListItem(Assessment a) {
    Map<String, Object> map = base(a);
    map.put("organization", a.getOrganization() != null ? organizationMapper.toMap(a.getOrganization()) : null);
    map.put("assessor", a.getAssessor() != null ? toAssessorSummary(a.getAssessor()) : null);
    map.put("certificates", a.getCertificates().stream().map(this::toCertificate).toList());
    return map;
  }

  private Map<String, Object> base(Assessment a) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", a.getId());
    map.put("org_id", a.getOrganization() != null ? a.getOrganization().getId() : null);
    map.put("assessor_user_id", a.getAssessor() != null ? a.getAssessor().getId() : null);
    map.put("assessment_year", a.getAssessmentYear());
    map.put("status", a.getStatus());
    map.put("total_score", a.getTotalScore());
    map.put("notes", a.getNotes());
    map.put("certified_level", a.getCertifiedLevel());
    map.put("submitted_at", a.getSubmittedAt());
    map.put("created_at", a.getCreatedAt());
    map.put("updated_at", a.getUpdatedAt());
    return map;
  }

  private Map<String, Object> toAssessorSummary(User user) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", user.getId());
    map.put("email", user.getEmail());
    if (user.getUserProfile() != null) {
      Map<String, Object> profile = new LinkedHashMap<>();
      profile.put("first_name", user.getUserProfile().getFirstName());
      profile.put("last_name", user.getUserProfile().getLastName());
      map.put("user_profile", profile);
    }
    return map;
  }

  public Map<String, Object> toDetailItem(AssessmentDetail d) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", d.getId());
    map.put("assessment_id", d.getAssessment() != null ? d.getAssessment().getId() : null);
    map.put("criteria_id", d.getCriteria() != null ? d.getCriteria().getId() : null);
    map.put("self_score", d.getSelfScore());
    map.put("applicant_comment", d.getApplicantComment());
    map.put("assessor_score", d.getAssessorScore());
    map.put("auditor_comment", d.getAuditorComment());
    map.put("created_at", d.getCreatedAt());
    map.put("updated_at", d.getUpdatedAt());
    map.put("criteria", d.getCriteria() != null ? toCriteria(d.getCriteria()) : null);
    map.put("evidence_files", d.getEvidenceFiles().stream().map(this::toEvidenceFile).toList());
    return map;
  }

  public Map<String, Object> toCriteria(GreenCriteriaMaster c) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", c.getId());
    map.put("category_number", c.getCategoryNumber());
    map.put("criteria_code", c.getCriteriaCode());
    map.put("criteria_name", c.getCriteriaName());
    map.put("max_score", c.getMaxScore());
    map.put("description", c.getDescription());
    map.put("year_version", c.getYearVersion());
    map.put("created_at", c.getCreatedAt());
    return map;
  }

  public Map<String, Object> toEvidenceFile(EvidenceFile f) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", f.getId());
    map.put("assessment_detail_id", f.getAssessmentDetail() != null ? f.getAssessmentDetail().getId() : null);
    map.put("uploaded_by_user_id", f.getUploadedBy() != null ? f.getUploadedBy().getId() : null);
    map.put("carbon_log_id", f.getCarbonLogId());
    map.put("file_name", f.getFileName());
    map.put("file_url", f.getFileUrl());
    map.put("file_type", f.getFileType());
    map.put("file_size", f.getFileSize());
    map.put("category", f.getCategory());
    map.put("uploaded_at", f.getUploadedAt());
    return map;
  }

  public Map<String, Object> toCertificate(Certificate c) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", c.getId());
    map.put("certificate_no", c.getCertificateNo());
    map.put("assessment_id", c.getAssessment() != null ? c.getAssessment().getId() : null);
    map.put("org_id", c.getOrganization() != null ? c.getOrganization().getId() : null);
    map.put("issued_at", c.getIssuedAt());
    map.put("expired_at", c.getExpiredAt());
    map.put("certificate_url", c.getCertificateUrl());
    map.put("created_at", c.getCreatedAt());
    return map;
  }
}
