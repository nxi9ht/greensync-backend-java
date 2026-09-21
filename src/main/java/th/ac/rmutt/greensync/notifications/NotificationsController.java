package th.ac.rmutt.greensync.notifications;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.notifications.dto.CreateBulkNotificationRequest;
import th.ac.rmutt.greensync.notifications.dto.CreateNotificationRequest;
import th.ac.rmutt.greensync.notifications.dto.ProposeAcademicChangeRequest;
import th.ac.rmutt.greensync.notifications.dto.RejectReasonRequest;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

@RestController
@RequestMapping("/notifications")
public class NotificationsController {

  private final NotificationsService notificationsService;

  public NotificationsController(NotificationsService notificationsService) {
    this.notificationsService = notificationsService;
  }

  @GetMapping("/system/history")
  @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
  public List<Map<String, Object>> findAllSystemWide(
      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer limit) {
    return notificationsService.findAllSystemWide(page, limit);
  }

  @GetMapping
  public List<Map<String, Object>> findAll(
      @AuthenticationPrincipal AuthenticatedUser me,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer limit) {
    return notificationsService.findAllForUser(me.userId(), page, limit);
  }

  @GetMapping("/unread-count")
  public long getUnreadCount(@AuthenticationPrincipal AuthenticatedUser me) {
    return notificationsService.getUnreadCount(me.userId());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN','ORG_ADMIN','EXECUTIVE','EMPLOYEE','USER','ASSESSOR')")
  public Map<String, Object> create(
      @RequestBody CreateNotificationRequest body, @AuthenticationPrincipal AuthenticatedUser me) {
    return notificationsService.create(body.title, body.message, body.type, body.recipient_id, me.userId(), body.link);
  }

  @PostMapping("/bulk")
  @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN','ORG_ADMIN')")
  public List<Map<String, Object>> createBulk(
      @RequestBody CreateBulkNotificationRequest body, @AuthenticationPrincipal AuthenticatedUser me) {
    return notificationsService.createBulk(
        body.title, body.message, body.type, body.recipient_ids, me.userId(), body.link);
  }

  @PatchMapping("/{id}/read")
  public Map<String, Object> markAsRead(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    return notificationsService.markAsRead(id, me.userId());
  }

  @PatchMapping("/read-all")
  public void markAllAsRead(@AuthenticationPrincipal AuthenticatedUser me) {
    notificationsService.markAllAsRead(me.userId());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
  public void remove(@PathVariable Integer id) {
    notificationsService.remove(id);
  }

  @PostMapping("/propose-academic")
  @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN','ASSESSOR')")
  public Map<String, Object> proposeAcademic(
      @RequestBody ProposeAcademicChangeRequest body, @AuthenticationPrincipal AuthenticatedUser me) {
    return notificationsService.proposeAcademicChange(me.userId(), body);
  }

  @PostMapping("/{id}/approve-academic")
  @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
  public Map<String, Object> approveAcademic(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    return notificationsService.approveAcademicChange(id, me.userId());
  }

  @PostMapping("/{id}/reject-academic")
  @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
  public Map<String, Object> rejectAcademic(
      @PathVariable Integer id, @RequestBody RejectReasonRequest body, @AuthenticationPrincipal AuthenticatedUser me) {
    return notificationsService.rejectAcademicChange(id, me.userId(), body.reason);
  }
}
