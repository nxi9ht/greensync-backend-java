package th.ac.rmutt.greensync.assessoradmin;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.users.UsersService;
import th.ac.rmutt.greensync.users.dto.UpdateUserRequest;

@RestController
@RequestMapping("/assessor-admin")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ASSESSOR_ADMIN')")
public class AssessorAdminController {

  private final UsersService usersService;
  private final AssessorAdminService assessorAdminService;

  public AssessorAdminController(UsersService usersService, AssessorAdminService assessorAdminService) {
    this.usersService = usersService;
    this.assessorAdminService = assessorAdminService;
  }

  @GetMapping("/dashboard")
  public Map<String, Object> getDashboard() {
    return assessorAdminService.getDashboardStats();
  }

  @GetMapping("/assessors")
  public List<Map<String, Object>> findAllAssessors() {
    return usersService.findAll("ASSESSOR", null, null, null);
  }

  @GetMapping("/assessors/{id}")
  public Map<String, Object> findOneAssessor(@PathVariable Integer id) {
    return usersService.getDetail(id);
  }

  @PatchMapping("/assessors/{id}/status")
  public Map<String, Object> updateAssessorStatus(@PathVariable Integer id, @RequestBody Map<String, Boolean> body) {
    UpdateUserRequest req = new UpdateUserRequest();
    req.is_active = body.get("is_active");
    usersService.update(id, req);
    return usersService.getDetail(id);
  }

  @DeleteMapping("/assessors/{id}")
  public void removeAssessor(@PathVariable Integer id) {
    usersService.remove(id);
  }

  @PostMapping("/assignments")
  public Map<String, Object> assignAssessor(@RequestBody Map<String, Integer> body) {
    return assessorAdminService.assignAssessor(body.get("assessmentId"), body.get("assessorId"));
  }

  @GetMapping("/performance/{assessorId}")
  public Map<String, Object> getPerformance(@PathVariable Integer assessorId) {
    return assessorAdminService.getAssessorPerformance(assessorId);
  }

  @PostMapping("/payouts")
  public Map<String, Object> processPayout(@RequestBody Map<String, Object> body) {
    Integer assessorId = (Integer) body.get("assessorId");
    Double amount = ((Number) body.get("amount")).doubleValue();
    return assessorAdminService.processPayout(assessorId, amount);
  }
}
