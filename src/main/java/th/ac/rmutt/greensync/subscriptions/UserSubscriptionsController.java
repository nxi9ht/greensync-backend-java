package th.ac.rmutt.greensync.subscriptions;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.organizations.Organization;
import th.ac.rmutt.greensync.security.AuthenticatedUser;
import th.ac.rmutt.greensync.subscriptions.dto.SubscribePaidRequest;
import th.ac.rmutt.greensync.subscriptions.dto.SubscribeRequest;

@RestController
@RequestMapping("/subscriptions")
public class UserSubscriptionsController {

  private final SubscriptionsService subscriptionsService;

  public UserSubscriptionsController(SubscriptionsService subscriptionsService) {
    this.subscriptionsService = subscriptionsService;
  }

  @GetMapping("/my")
  public ResponseEntity<Map<String, Object>> getMySubscription(@AuthenticationPrincipal AuthenticatedUser me) {
    Organization org = subscriptionsService.getOrganizationByUserId(me.userId());
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0")
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header(HttpHeaders.EXPIRES, "0")
        .body(subscriptionsService.findOrgSubscription(org.getId()));
  }

  @GetMapping("/my/usage")
  public List<Map<String, Object>> getMyUsage(@AuthenticationPrincipal AuthenticatedUser me) {
    Organization org = subscriptionsService.getOrganizationByUserId(me.userId());
    return subscriptionsService.getOrganizationFeatureQuotaSummary(org.getId());
  }

  @GetMapping("/my/payments")
  public List<Map<String, Object>> getMyPayments(@AuthenticationPrincipal AuthenticatedUser me) {
    Organization org = subscriptionsService.getOrganizationByUserId(me.userId());
    return subscriptionsService.getOrganizationPayments(org.getId());
  }

  // Note: GET /subscriptions/plans is served by SubscriptionsController (public, no auth
  // required) — the NestJS original registered the same route on both controllers, which
  // Spring MVC (unlike Nest) treats as an ambiguous mapping error, so it is not repeated here.

  @PostMapping("/my/subscribe")
  public Map<String, Object> subscribeToPlan(
      @RequestBody SubscribeRequest body, @AuthenticationPrincipal AuthenticatedUser me) {
    Organization org = subscriptionsService.getOrganizationByUserId(me.userId());
    return subscriptionsService.subscribeToPlan(org.getId(), body.planId);
  }

  @PostMapping("/my/subscribe-paid")
  public Map<String, Object> subscribeToPaidPlan(
      @RequestBody SubscribePaidRequest body, @AuthenticationPrincipal AuthenticatedUser me) {
    Organization org = subscriptionsService.getOrganizationByUserId(me.userId());
    return subscriptionsService.subscribeToPaidPlan(org.getId(), body.planId, body.paymentMethodId);
  }
}
