package th.ac.rmutt.greensync.assessments;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.common.ApiException;

@Service
public class GreenCriteriaService {

  private static final Logger log = LoggerFactory.getLogger(GreenCriteriaService.class);
  private static final long CACHE_TTL_MS = 60_000;

  private final GreenCriteriaMasterRepository criteriaRepository;
  private final AssessmentRepository assessmentRepository;
  private final AssessmentDetailRepository assessmentDetailRepository;
  private final AssessmentMapper assessmentMapper;

  private volatile List<GreenCriteriaMaster> cachedCriteria;
  private volatile long lastFetchTime;

  public GreenCriteriaService(
      GreenCriteriaMasterRepository criteriaRepository,
      AssessmentRepository assessmentRepository,
      AssessmentDetailRepository assessmentDetailRepository,
      AssessmentMapper assessmentMapper) {
    this.criteriaRepository = criteriaRepository;
    this.assessmentRepository = assessmentRepository;
    this.assessmentDetailRepository = assessmentDetailRepository;
    this.assessmentMapper = assessmentMapper;
  }

  private List<GreenCriteriaMaster> masterCriteria() {
    long now = System.currentTimeMillis();
    if (cachedCriteria != null && now - lastFetchTime < CACHE_TTL_MS) {
      return cachedCriteria;
    }
    cachedCriteria = criteriaRepository.findAllOrdered();
    lastFetchTime = now;
    return cachedCriteria;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAll(Integer orgId) {
    boolean hasPassed =
        orgId != null && orgId > 0
            && assessmentRepository.findFirstByOrganizationIdAndStatus(orgId, "APPROVED").isPresent();

    List<GreenCriteriaMaster> criteria = masterCriteria();
    if (orgId != null && orgId > 0 && !hasPassed) {
      criteria = criteria.stream().filter(c -> !Integer.valueOf(7).equals(c.getCategoryNumber())).toList();
    }
    return criteria.stream().map(assessmentMapper::toCriteria).toList();
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAllForFrontend(Integer orgId) {
    boolean hasPassed = false;
    Integer pendingAssId = null;
    if (orgId != null && orgId > 0) {
      hasPassed = assessmentRepository.findFirstByOrganizationIdAndStatus(orgId, "APPROVED").isPresent();
      pendingAssId =
          assessmentRepository
              .findFirstByOrganizationIdAndStatus(orgId, "PENDING")
              .map(Assessment::getId)
              .orElse(null);
    }

    List<GreenCriteriaMaster> criteria = masterCriteria();
    List<GreenCriteriaMaster> filtered =
        orgId != null && orgId > 0 && !hasPassed
            ? criteria.stream().filter(c -> !Integer.valueOf(7).equals(c.getCategoryNumber())).toList()
            : criteria;

    List<AssessmentDetail> details =
        pendingAssId != null ? assessmentDetailRepository.findByAssessmentId(pendingAssId) : List.of();

    return filtered.stream()
        .map(
            item -> {
              AssessmentDetail detail =
                  details.stream()
                      .filter(d -> d.getCriteria() != null && d.getCriteria().getId().equals(item.getId()))
                      .findFirst()
                      .orElse(null);
              Map<String, Object> map = new LinkedHashMap<>();
              map.put("id", item.getId());
              map.put("category", item.getCategoryNumber());
              map.put("code", item.getCriteriaCode());
              map.put("name", item.getCriteriaName());
              map.put("maxScore", item.getMaxScore());
              double currentScore = detail != null && detail.getSelfScore() != null ? detail.getSelfScore() : 0;
              map.put("currentScore", currentScore);
              map.put("status", currentScore > 0 ? "Completed" : "Pending");
              return map;
            })
        .toList();
  }

  @Transactional
  public Map<String, Object> create(th.ac.rmutt.greensync.assessments.dto.GreenCriteriaRequest data) {
    cachedCriteria = null;
    GreenCriteriaMaster entity = new GreenCriteriaMaster();
    applyFields(entity, data);
    GreenCriteriaMaster saved = criteriaRepository.save(entity);
    log.info("Added new criteria: {}", saved.getCriteriaName());
    return assessmentMapper.toCriteria(saved);
  }

  @Transactional
  public Map<String, Object> update(Integer id, th.ac.rmutt.greensync.assessments.dto.GreenCriteriaRequest data) {
    cachedCriteria = null;
    GreenCriteriaMaster existing =
        criteriaRepository.findById(id).orElseThrow(() -> ApiException.notFound("ไม่พบเกณฑ์ที่ระบุ"));
    applyFields(existing, data);
    GreenCriteriaMaster saved = criteriaRepository.save(existing);
    log.info("Updated criteria: {}", saved.getCriteriaName());
    return assessmentMapper.toCriteria(saved);
  }

  private void applyFields(GreenCriteriaMaster entity, th.ac.rmutt.greensync.assessments.dto.GreenCriteriaRequest data) {
    if (data.category_number != null) entity.setCategoryNumber(data.category_number);
    if (data.criteria_code != null) entity.setCriteriaCode(data.criteria_code);
    if (data.criteria_name != null) entity.setCriteriaName(data.criteria_name);
    if (data.max_score != null) entity.setMaxScore(data.max_score);
    if (data.description != null) entity.setDescription(data.description);
    if (data.year_version != null) entity.setYearVersion(data.year_version);
  }

  @Transactional
  public void remove(Integer id) {
    cachedCriteria = null;
    criteriaRepository.findById(id).ifPresent(item -> log.info("Deleted criteria: {}", item.getCriteriaName()));
    criteriaRepository.deleteById(id);
  }

  @Transactional
  public Map<String, Object> updateScore(Integer criteriaId, Double score, Integer orgId) {
    if (orgId == null || orgId == 0) {
      return Map.of("success", false, "message", "Organization ID is required");
    }

    Assessment assessment =
        assessmentRepository
            .findFirstByOrganizationIdAndStatus(orgId, "PENDING")
            .orElseGet(
                () -> {
                  Assessment a = new Assessment();
                  a.setStatus("PENDING");
                  a.setTotalScore(0.0);
                  // organization is set via a managed reference below once persisted.
                  return a;
                });

    if (assessment.getId() == null) {
      // brand-new assessment: attach the organization reference and persist
      var org = new th.ac.rmutt.greensync.organizations.Organization();
      org.setId(orgId);
      assessment.setOrganization(org);
      assessment = assessmentRepository.save(assessment);
    }
    final Assessment currentAssessment = assessment;

    AssessmentDetail detail =
        assessmentDetailRepository
            .findByAssessmentIdAndCriteriaId(currentAssessment.getId(), criteriaId)
            .orElseGet(
                () -> {
                  AssessmentDetail d = new AssessmentDetail();
                  d.setAssessment(currentAssessment);
                  GreenCriteriaMaster criteria =
                      criteriaRepository.findById(criteriaId).orElseThrow(() -> ApiException.notFound("ไม่พบเกณฑ์ที่ระบุ"));
                  d.setCriteria(criteria);
                  return d;
                });
    detail.setSelfScore(score);
    assessmentDetailRepository.save(detail);

    double totalScore =
        assessmentDetailRepository.findByAssessmentId(assessment.getId()).stream()
            .mapToDouble(d -> d.getSelfScore() != null ? d.getSelfScore() : 0)
            .sum();
    assessment.setTotalScore(totalScore);
    assessmentRepository.save(assessment);

    log.info("Updated score for criteria {} to {} (org {})", criteriaId, score, orgId);
    return Map.of("success", true, "criteriaId", criteriaId, "score", score, "totalScore", totalScore);
  }
}
