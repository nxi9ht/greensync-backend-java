package th.ac.rmutt.greensync.uploads;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import th.ac.rmutt.greensync.assessments.AssessmentDetailRepository;
import th.ac.rmutt.greensync.assessments.AssessmentMapper;
import th.ac.rmutt.greensync.assessments.EvidenceFile;
import th.ac.rmutt.greensync.assessments.EvidenceFileRepository;
import th.ac.rmutt.greensync.carbonlogs.CarbonLogRepository;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.security.AuthenticatedUser;
import th.ac.rmutt.greensync.users.User;

@Service
public class UploadsService {

  private static final Set<String> ALLOWED_FOLDERS = Set.of("evidence", "certificates", "avatars", "reports", "general");
  private static final Set<String> UPLOAD_ANY_ORG_ROLES = Set.of("SYSTEMADMIN", "ASSESSOR", "ASSESSORADMIN");
  private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/png", "image/jpeg", "image/jpg", "application/pdf");

  private final SupabaseStorageService storageService;
  private final EvidenceFileRepository evidenceFileRepository;
  private final AssessmentDetailRepository assessmentDetailRepository;
  private final CarbonLogRepository carbonLogRepository;
  private final AssessmentMapper assessmentMapper;

  public UploadsService(
      SupabaseStorageService storageService,
      EvidenceFileRepository evidenceFileRepository,
      AssessmentDetailRepository assessmentDetailRepository,
      CarbonLogRepository carbonLogRepository,
      AssessmentMapper assessmentMapper) {
    this.storageService = storageService;
    this.evidenceFileRepository = evidenceFileRepository;
    this.assessmentDetailRepository = assessmentDetailRepository;
    this.carbonLogRepository = carbonLogRepository;
    this.assessmentMapper = assessmentMapper;
  }

  private String normalizeRole(String role) {
    return role == null ? "" : role.trim().toUpperCase().replaceAll("[\\s_]", "");
  }

  public String validateFolder(String folder) {
    String cleanFolder = (folder == null ? "evidence" : folder).trim().toLowerCase();
    if (!ALLOWED_FOLDERS.contains(cleanFolder)) {
      throw ApiException.badRequest("โฟลเดอร์สำหรับจัดเก็บไฟล์ไม่ถูกต้อง");
    }
    return cleanFolder;
  }

  public Integer resolveTargetUserId(AuthenticatedUser me, Integer requestedUserId) {
    String role = normalizeRole(me.role());
    if (UPLOAD_ANY_ORG_ROLES.contains(role) && requestedUserId != null) {
      return requestedUserId;
    }
    return me.userId();
  }

  @Transactional
  public Map<String, Object> uploadFile(
      MultipartFile file,
      String folder,
      Integer assessmentDetailId,
      Integer targetUserId,
      Integer carbonLogId,
      String category,
      AuthenticatedUser me) {
    if (file == null || file.isEmpty()) {
      throw ApiException.badRequest("No file uploaded");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw ApiException.badRequest("ขนาดไฟล์ต้องไม่เกิน 5MB");
    }
    if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
      throw ApiException.badRequest("รองรับเฉพาะไฟล์ .png, .jpeg, .jpg, .pdf เท่านั้น");
    }

    String role = normalizeRole(me.role());
    if (!UPLOAD_ANY_ORG_ROLES.contains(role)) {
      Integer orgId = me.orgId();
      if (assessmentDetailId != null) {
        assessmentDetailRepository
            .findById(assessmentDetailId)
            .ifPresent(
                detail -> {
                  var assessment = detail.getAssessment();
                  if (assessment != null
                      && assessment.getOrganization() != null
                      && !assessment.getOrganization().getId().equals(orgId)) {
                    throw ApiException.forbidden("คุณไม่มีสิทธิ์อัปโหลดไฟล์ให้การประเมินขององค์กรอื่น");
                  }
                });
      }
      if (carbonLogId != null) {
        carbonLogRepository
            .findById(carbonLogId)
            .ifPresent(
                log -> {
                  if (log.getOrganization() != null && !log.getOrganization().getId().equals(orgId)) {
                    throw ApiException.forbidden("คุณไม่มีสิทธิ์อัปโหลดไฟล์ให้ประวัติคาร์บอนขององค์กรอื่น");
                  }
                });
      }
    }

    if (!storageService.isConfigured()) {
      throw ApiException.badRequest("Upload system is not ready (Supabase missing)");
    }

    String safeName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename().replaceAll("[^a-zA-Z0-9.]", "_");
    String path = folder + "/" + System.currentTimeMillis() + "-" + safeName;

    byte[] content;
    try {
      content = file.getBytes();
    } catch (Exception e) {
      throw ApiException.badRequest("ไม่สามารถอ่านไฟล์ที่อัปโหลดได้");
    }

    String fileUrl = storageService.upload(path, content, file.getContentType());

    if ("certificates".equals(folder)) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", 0);
      result.put("file_name", file.getOriginalFilename());
      result.put("file_url", fileUrl);
      result.put("file_type", file.getContentType());
      result.put("file_size", file.getSize());
      result.put("uploaded_at", java.time.Instant.now());
      return result;
    }

    EvidenceFile evidenceFile = new EvidenceFile();
    evidenceFile.setFileName(file.getOriginalFilename());
    evidenceFile.setFileUrl(fileUrl);
    evidenceFile.setFileType(file.getContentType());
    evidenceFile.setFileSize(file.getSize());
    if (assessmentDetailId != null) {
      assessmentDetailRepository.findById(assessmentDetailId).ifPresent(evidenceFile::setAssessmentDetail);
    }
    if (targetUserId != null) {
      User uploadedBy = new User();
      uploadedBy.setId(targetUserId);
      evidenceFile.setUploadedBy(uploadedBy);
    }
    evidenceFile.setCarbonLogId(carbonLogId);
    evidenceFile.setCategory(category);

    EvidenceFile saved = evidenceFileRepository.save(evidenceFile);
    return assessmentMapper.toEvidenceFile(saved);
  }

  private void assertOwnership(EvidenceFile file, AuthenticatedUser me) {
    String role = normalizeRole(me.role());
    if (UPLOAD_ANY_ORG_ROLES.contains(role)) return;

    Integer orgId = me.orgId();
    if (orgId == null) {
      throw ApiException.forbidden("คุณไม่มีสิทธิ์เข้าถึงไฟล์นี้ (ไม่ระบุองค์กร)");
    }

    Integer fileOrgId = null;
    if (file.getUploadedBy() != null && file.getUploadedBy().getOrganization() != null) {
      fileOrgId = file.getUploadedBy().getOrganization().getId();
    }
    if (file.getAssessmentDetail() != null
        && file.getAssessmentDetail().getAssessment() != null
        && file.getAssessmentDetail().getAssessment().getOrganization() != null) {
      fileOrgId = file.getAssessmentDetail().getAssessment().getOrganization().getId();
    }
    if (file.getCarbonLogId() != null) {
      var log = carbonLogRepository.findById(file.getCarbonLogId()).orElse(null);
      if (log != null && log.getOrganization() != null) {
        fileOrgId = log.getOrganization().getId();
      }
    }

    if (fileOrgId == null || !fileOrgId.equals(orgId)) {
      throw ApiException.forbidden("คุณไม่มีสิทธิ์เข้าถึงหรือจัดการไฟล์ขององค์กรอื่น");
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Object> findOne(Integer id, AuthenticatedUser me) {
    EvidenceFile file = evidenceFileRepository.findById(id).orElseThrow(() -> ApiException.badRequest("File not found"));
    assertOwnership(file, me);
    return assessmentMapper.toEvidenceFile(file);
  }

  @Transactional
  public Map<String, Object> deleteFile(Integer id, AuthenticatedUser me) {
    EvidenceFile file = evidenceFileRepository.findById(id).orElseThrow(() -> ApiException.badRequest("File not found"));
    assertOwnership(file, me);

    String path = storageService.extractPath(file.getFileUrl());
    if (path != null) {
      storageService.delete(path);
    }
    evidenceFileRepository.deleteById(id);
    return Map.of("success", true);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAll(AuthenticatedUser me, Integer page, Integer limit) {
    String role = normalizeRole(me.role());
    int safeLimit = limit != null ? Math.min(Math.max(1, limit), 200) : 100;
    int safePage = page != null ? Math.max(1, page) : 1;

    List<EvidenceFile> all =
        evidenceFileRepository.findAllWithRelations().stream()
            .filter(f -> f.getFileUrl() == null || !f.getFileUrl().contains("/certificates/"))
            .toList();

    if (!UPLOAD_ANY_ORG_ROLES.contains(role)) {
      Integer orgId = me.orgId();
      all =
          all.stream()
              .filter(
                  f -> {
                    Integer fileOrgId = null;
                    if (f.getUploadedBy() != null && f.getUploadedBy().getOrganization() != null) {
                      fileOrgId = f.getUploadedBy().getOrganization().getId();
                    } else if (f.getAssessmentDetail() != null
                        && f.getAssessmentDetail().getAssessment() != null
                        && f.getAssessmentDetail().getAssessment().getOrganization() != null) {
                      fileOrgId = f.getAssessmentDetail().getAssessment().getOrganization().getId();
                    }
                    return fileOrgId != null && fileOrgId.equals(orgId);
                  })
              .toList();
    }

    if (page != null || limit != null) {
      int from = Math.min((safePage - 1) * safeLimit, all.size());
      int to = Math.min(from + safeLimit, all.size());
      all = all.subList(from, to);
    }

    return all.stream().map(assessmentMapper::toEvidenceFile).toList();
  }

  @Transactional
  public Map<String, Object> update(Integer id, String category, AuthenticatedUser me) {
    EvidenceFile file = evidenceFileRepository.findById(id).orElseThrow(() -> ApiException.badRequest("File not found"));
    assertOwnership(file, me);
    file.setCategory(category);
    return assessmentMapper.toEvidenceFile(evidenceFileRepository.save(file));
  }
}
