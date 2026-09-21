package th.ac.rmutt.greensync.subscriptions;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.subscriptions.dto.FeatureRequest;
import th.ac.rmutt.greensync.subscriptions.dto.InvoiceStatusRequest;
import th.ac.rmutt.greensync.subscriptions.dto.PlanRequest;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionsController {

  private final SubscriptionsService subscriptionsService;

  public SubscriptionsController(SubscriptionsService subscriptionsService) {
    this.subscriptionsService = subscriptionsService;
  }

  // --- Public APIs (no login required) ---
  @GetMapping("/plans")
  public List<Map<String, Object>> findAllPlans() {
    return subscriptionsService.findAllPlans();
  }

  @GetMapping("/features")
  public List<Map<String, Object>> findAllFeatures() {
    return subscriptionsService.findAllFeatures();
  }

  // --- User / Organization APIs ---
  @GetMapping("/status")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','USER','EXECUTIVE')")
  public Map<String, Object> getStatus(@AuthenticationPrincipal AuthenticatedUser me) {
    return subscriptionsService.getUserSubscriptionStatusByUserId(me.userId());
  }

  @GetMapping("/payments")
  @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ORG_ADMIN','USER','EXECUTIVE')")
  public List<Map<String, Object>> getPayments(@AuthenticationPrincipal AuthenticatedUser me) {
    return subscriptionsService.getOrganizationPayments(me.orgId());
  }

  @DeleteMapping("/my/cancel")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','EXECUTIVE')")
  public Map<String, Object> cancelMySubscription(@AuthenticationPrincipal AuthenticatedUser me) {
    return subscriptionsService.cancelSubscription(me.orgId());
  }

  // --- System Admin APIs ---
  @PostMapping("/features")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public Map<String, Object> createFeature(@RequestBody FeatureRequest data) {
    return subscriptionsService.createFeature(data);
  }

  @PutMapping("/features/{id}")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public Map<String, Object> updateFeature(@PathVariable Integer id, @RequestBody FeatureRequest data) {
    return subscriptionsService.updateFeature(id, data);
  }

  @DeleteMapping("/features/{id}")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public void removeFeature(@PathVariable Integer id) {
    subscriptionsService.removeFeature(id);
  }

  @PostMapping("/plans")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public Map<String, Object> createPlan(@RequestBody PlanRequest data) {
    return subscriptionsService.createPlan(data);
  }

  @PutMapping("/plans/{id}")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public Map<String, Object> updatePlan(@PathVariable Integer id, @RequestBody PlanRequest data) {
    return subscriptionsService.updatePlan(id, data);
  }

  @DeleteMapping("/plans/{id}")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public void removePlan(@PathVariable Integer id) {
    subscriptionsService.removePlan(id);
  }

  @GetMapping("/invoices")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public List<Map<String, Object>> findAllInvoices(
      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer limit) {
    return subscriptionsService.findAllInvoices(page, limit);
  }

  @PutMapping("/invoices/{id}/status")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public Map<String, Object> updateInvoiceStatus(@PathVariable Integer id, @RequestBody InvoiceStatusRequest body) {
    return subscriptionsService.updateInvoiceStatus(id, body.status);
  }

  @GetMapping("/usage")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','EXECUTIVE')")
  public List<Map<String, Object>> getUsageLogs(
      @AuthenticationPrincipal AuthenticatedUser me,
      @RequestParam(required = false) Integer month,
      @RequestParam(required = false) Integer year) {
    return subscriptionsService.getFeatureUsageLogs(me.orgId(), month, year);
  }

  @GetMapping("/my/quotas")
  @PreAuthorize("hasAnyRole('ORG_ADMIN','SYSTEM_ADMIN','EXECUTIVE')")
  public ResponseEntity<List<Map<String, Object>>> getMyQuotas(@AuthenticationPrincipal AuthenticatedUser me) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0")
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header(HttpHeaders.EXPIRES, "0")
        .body(subscriptionsService.getOrganizationFeatureQuotaSummary(me.orgId()));
  }
}
