package th.ac.rmutt.greensync.auditlogs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.users.User;

@Service
public class AuditLogsService {

  private final AuditLogRepository repository;

  public AuditLogsService(AuditLogRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void logAction(Integer userId, String action, String comment) {
    logAction(userId, action, comment, null);
  }

  @Transactional
  public void logAction(Integer userId, String action, String comment, Integer assessmentDetailId) {
    AuditLog log = new AuditLog();
    if (userId != null) {
      User user = new User();
      user.setId(userId);
      log.setUser(user);
    }
    log.setAction(action);
    log.setComment(comment);
    log.setAssessmentDetailId(assessmentDetailId);
    repository.save(log);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAll(Integer page, Integer limit, Integer orgId) {
    int safeLimit = limit != null ? Math.min(Math.max(1, limit), 200) : 50;
    int safePage = page != null ? Math.max(1, page) : 1;
    return repository.search(orgId, PageRequest.of(safePage - 1, safeLimit)).stream().map(this::toMap).toList();
  }

  private Map<String, Object> toMap(AuditLog log) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", log.getId());
    map.put("assessment_detail_id", log.getAssessmentDetailId());
    map.put("action_by_user_id", log.getUser() != null ? log.getUser().getId() : null);
    map.put("action", log.getAction());
    map.put("comment", log.getComment());
    map.put("created_at", log.getCreatedAt());
    if (log.getUser() != null) {
      Map<String, Object> user = new LinkedHashMap<>();
      user.put("id", log.getUser().getId());
      user.put("email", log.getUser().getEmail());
      map.put("user", user);
    }
    return map;
  }
}
