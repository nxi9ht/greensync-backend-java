package th.ac.rmutt.greensync.users;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.organizations.OrganizationRepository;
import th.ac.rmutt.greensync.security.AuthenticatedUser;
import th.ac.rmutt.greensync.users.dto.CreateUserRequest;
import th.ac.rmutt.greensync.users.dto.UpdateProfileRequest;
import th.ac.rmutt.greensync.users.dto.UpdateUserRequest;

@RestController
@RequestMapping("/users")
public class UsersController {

  private static final List<String> ORG_ADMIN_ASSIGNABLE_ROLES =
      List.of("ORGADMIN", "ORGANIZATIONADMIN", "EXECUTIVE", "EMPLOYEE", "USER");

  private final UsersService usersService;
  private final OrganizationRepository organizationRepository;

  public UsersController(UsersService usersService, OrganizationRepository organizationRepository) {
    this.usersService = usersService;
    this.organizationRepository = organizationRepository;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','ASSESSOR_ADMIN')")
  public List<Map<String, Object>> findAll(
      @RequestParam(required = false) String role,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer limit,
      @AuthenticationPrincipal AuthenticatedUser me) {
    Integer orgId = "ORG_ADMIN".equals(me.role()) ? me.orgId() : null;
    return usersService.findAll(role, orgId, page, limit);
  }

  @GetMapping("/roles")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public List<Role> getAllRoles() {
    return usersService.getAllRoles();
  }

  @GetMapping("/profile/me")
  public Map<String, Object> getMyProfile(@AuthenticationPrincipal AuthenticatedUser me) {
    return usersService.getDetail(me.userId());
  }

  @PatchMapping("/profile/me")
  public Map<String, Object> updateMyProfile(
      @AuthenticationPrincipal AuthenticatedUser me, @RequestBody UpdateProfileRequest request) {
    usersService.updateProfileOnly(me.userId(), request);
    return usersService.getDetail(me.userId());
  }

  @PostMapping("/profile/goals")
  public Map<String, Object> setPersonalGoal(
      @AuthenticationPrincipal AuthenticatedUser me, @RequestBody Map<String, BigDecimal> body) {
    usersService.setPersonalGoal(me.userId(), body.get("targetReductionPercent"));
    return Map.of("success", true, "targetReductionPercent", body.get("targetReductionPercent"));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public Map<String, Object> create(
      @Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal AuthenticatedUser me) {
    var org =
        "ORG_ADMIN".equals(me.role()) ? organizationRepository.findById(me.orgId()).orElseThrow() : null;
    if ("ORG_ADMIN".equals(me.role())) {
      assertOrgAdminAssignableRole(request.role);
    }
    User created = usersService.createFromRequest(request, org);
    return usersService.getDetail(created.getId());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN') or #id == principal.userId()")
  public Map<String, Object> findOne(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    Map<String, Object> detail = usersService.getDetail(id);
    if ("ORG_ADMIN".equals(me.role()) && !isSameOrg(detail, me)) {
      throw ApiException.forbidden("ไม่มีสิทธิ์ดูผู้ใช้งานนอกองค์กร");
    }
    return detail;
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public Map<String, Object> update(
      @PathVariable Integer id,
      @RequestBody UpdateUserRequest request,
      @AuthenticationPrincipal AuthenticatedUser me) {
    if ("ORG_ADMIN".equals(me.role())) {
      Map<String, Object> existing = usersService.getDetail(id);
      if (!isSameOrg(existing, me)) {
        throw ApiException.forbidden("ไม่มีสิทธิ์แก้ไขผู้ใช้งานนอกองค์กร");
      }
      assertOrgAdminAssignableRole(request.role);
      if (request.organization != null
          && request.organization.id != null
          && !request.organization.id.equals(me.orgId())) {
        throw ApiException.forbidden("ไม่มีสิทธิ์ย้ายผู้ใช้งานไปองค์กรอื่น");
      }
    }
    usersService.update(id, request);
    return usersService.getDetail(id);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public void remove(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    if ("ORG_ADMIN".equals(me.role())) {
      Map<String, Object> existing = usersService.getDetail(id);
      if (!isSameOrg(existing, me)) {
        throw ApiException.forbidden("ไม่มีสิทธิ์ลบผู้ใช้งานนอกองค์กร");
      }
    }
    usersService.remove(id);
  }

  @SuppressWarnings("unchecked")
  private boolean isSameOrg(Map<String, Object> userDetail, AuthenticatedUser me) {
    Map<String, Object> org = (Map<String, Object>) userDetail.get("organization");
    return org != null && me.orgId() != null && me.orgId().equals(org.get("id"));
  }

  private void assertOrgAdminAssignableRole(String role) {
    if (role == null) return;
    String normalized = role.toUpperCase().replaceAll("[\\s_]", "");
    if (!ORG_ADMIN_ASSIGNABLE_ROLES.contains(normalized)) {
      throw ApiException.forbidden("ไม่มีสิทธิ์กำหนดบทบาทนี้");
    }
  }
}
