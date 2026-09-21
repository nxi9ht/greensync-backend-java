package th.ac.rmutt.greensync.orgadmin;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.orgadmin.dto.NotesRequest;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

@RestController
@RequestMapping("/org-admin")
@PreAuthorize("hasAnyRole('ORG_ADMIN','SYSTEM_ADMIN')")
public class OrgAdminController {

  private final OrgAdminService orgAdminService;

  public OrgAdminController(OrgAdminService orgAdminService) {
    this.orgAdminService = orgAdminService;
  }

  private Integer orgId(AuthenticatedUser me) {
    if (me.orgId() == null || me.orgId() == 0) {
      throw ApiException.badRequest("บัญชีนี้ไม่ได้เชื่อมกับองค์กร");
    }
    return me.orgId();
  }

  @GetMapping("/revision-center")
  public Map<String, Object> getRevisionCenter(@AuthenticationPrincipal AuthenticatedUser me) {
    return orgAdminService.getRevisionCenter(orgId(me));
  }

  @PatchMapping("/revision-center/{id}/send-to-user")
  public Map<String, Object> sendToUser(
      @PathVariable Integer id, @RequestBody NotesRequest request, @AuthenticationPrincipal AuthenticatedUser me) {
    return orgAdminService.sendToUser(id, orgId(me), request.notes);
  }

  @PatchMapping("/revision-center/{id}/resubmit")
  public Map<String, Object> resubmit(
      @PathVariable Integer id, @RequestBody NotesRequest request, @AuthenticationPrincipal AuthenticatedUser me) {
    return orgAdminService.resubmitRevision(id, orgId(me), request.notes);
  }
}
