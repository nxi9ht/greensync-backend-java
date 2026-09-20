package th.ac.rmutt.greensync.organizations;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.organizations.dto.OrganizationRequest;
import th.ac.rmutt.greensync.organizations.dto.OrganizationUnitRequest;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

@RestController
@RequestMapping("/organizations")
public class OrganizationsController {

  private final OrganizationsService organizationsService;

  public OrganizationsController(OrganizationsService organizationsService) {
    this.organizationsService = organizationsService;
  }

  private void assertOrgAdminAccess(AuthenticatedUser me, Integer orgId) {
    if ("ORG_ADMIN".equals(me.role()) && !orgId.equals(me.orgId())) {
      throw ApiException.forbidden("ไม่มีสิทธิ์เข้าถึงข้อมูลองค์กรอื่น");
    }
  }

  @PostMapping
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public Map<String, Object> create(@RequestBody OrganizationRequest request) {
    return organizationsService.create(request);
  }

  @GetMapping
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public List<Map<String, Object>> findAll() {
    return organizationsService.findAll();
  }

  @GetMapping("/export/csv")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public ResponseEntity<String> exportCsv() {
    String csv = organizationsService.exportCsv();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"organizations_export.csv\"")
        .body(csv);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','ASSESSOR','ASSESSOR_ADMIN')")
  public Map<String, Object> findOne(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    assertOrgAdminAccess(me, id);
    return organizationsService.findOne(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public Map<String, Object> update(
      @PathVariable Integer id, @RequestBody OrganizationRequest request, @AuthenticationPrincipal AuthenticatedUser me) {
    assertOrgAdminAccess(me, id);
    return organizationsService.update(id, request);
  }

  @GetMapping("/{id}/annual-report")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public Map<String, Object> getAnnualReport(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    assertOrgAdminAccess(me, id);
    return organizationsService.getAnnualReport(id);
  }

  // --- Organization Units ---

  @PostMapping("/{orgId}/units")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public Map<String, Object> createUnit(
      @PathVariable Integer orgId,
      @RequestBody OrganizationUnitRequest request,
      @AuthenticationPrincipal AuthenticatedUser me) {
    assertOrgAdminAccess(me, orgId);
    return organizationsService.createUnit(orgId, request);
  }

  @GetMapping("/{orgId}/units")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','ASSESSOR','ASSESSOR_ADMIN')")
  public List<Map<String, Object>> findUnits(
      @PathVariable Integer orgId, @AuthenticationPrincipal AuthenticatedUser me) {
    assertOrgAdminAccess(me, orgId);
    return organizationsService.findUnitsByOrg(orgId);
  }

  @PatchMapping("/units/{id}")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public Map<String, Object> updateUnit(
      @PathVariable Integer id,
      @RequestBody OrganizationUnitRequest request,
      @AuthenticationPrincipal AuthenticatedUser me) {
    OrganizationUnit unit = organizationsService.findUnitEntity(id);
    if (unit == null) throw ApiException.notFound("ไม่พบหน่วยงานย่อย");
    assertOrgAdminAccess(me, unit.getOrgId());
    return organizationsService.updateUnit(id, request);
  }

  @DeleteMapping("/units/{id}")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN')")
  public void removeUnit(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    OrganizationUnit unit = organizationsService.findUnitEntity(id);
    if (unit == null) throw ApiException.notFound("ไม่พบหน่วยงานย่อย");
    assertOrgAdminAccess(me, unit.getOrgId());
    organizationsService.removeUnit(id);
  }
}
