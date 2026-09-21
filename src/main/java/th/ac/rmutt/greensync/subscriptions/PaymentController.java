package th.ac.rmutt.greensync.subscriptions;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.organizations.Organization;
import th.ac.rmutt.greensync.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/** Stripe-backed payment-method management, ported structure-only (see {@link
 * SubscriptionsService}): every org here has no {@code stripe_customer_id}, so the list
 * endpoint legitimately returns empty exactly like the NestJS original did in that case, and
 * the mutating endpoints throw SERVICE_UNAVAILABLE instead of calling Stripe. */
@RestController
@RequestMapping("/payments")
public class PaymentController {

  private final SubscriptionsService subscriptionsService;

  public PaymentController(SubscriptionsService subscriptionsService) {
    this.subscriptionsService = subscriptionsService;
  }

  @PostMapping("/setup-intent")
  public Map<String, Object> createSetupIntent(@AuthenticationPrincipal AuthenticatedUser me) {
    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ระบบชำระเงินผ่าน Stripe ยังไม่พร้อมใช้งาน");
  }

  @GetMapping("/methods")
  public List<Map<String, Object>> listPaymentMethods(@AuthenticationPrincipal AuthenticatedUser me) {
    Organization org = subscriptionsService.getOrganizationByUserId(me.userId());
    if (org.getStripeCustomerId() == null) return List.of();
    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ระบบชำระเงินผ่าน Stripe ยังไม่พร้อมใช้งาน");
  }

  @DeleteMapping("/methods/{id}")
  public Map<String, Object> detachPaymentMethod(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser me) {
    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ระบบชำระเงินผ่าน Stripe ยังไม่พร้อมใช้งาน");
  }
}
