package th.ac.rmutt.greensync.subscriptions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.auditlogs.AuditLogsService;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.notifications.NotificationType;
import th.ac.rmutt.greensync.notifications.NotificationsService;
import th.ac.rmutt.greensync.organizations.Organization;
import th.ac.rmutt.greensync.organizations.OrganizationRepository;
import th.ac.rmutt.greensync.settings.SettingsService;
import th.ac.rmutt.greensync.subscriptions.dto.FeatureRequest;
import th.ac.rmutt.greensync.subscriptions.dto.PlanRequest;
import th.ac.rmutt.greensync.users.User;
import th.ac.rmutt.greensync.users.UserRepository;

/**
 * Subscriptions/billing module ported structure-only: the project has no live Stripe API key
 * (matches the pre-existing NestJS state), so every code path that would call Stripe throws a
 * clear {@code SERVICE_UNAVAILABLE} instead of pretending to charge a card. Free-plan signup,
 * plan/feature/invoice management, and quota bookkeeping are fully functional since none of
 * that touches Stripe.
 */
@Service
public class SubscriptionsService {

  private final SubscriptionPlanRepository planRepository;
  private final FeatureRepository featureRepository;
  private final InvoiceRepository invoiceRepository;
  private final OrganizationSubscriptionRepository orgSubRepository;
  private final FeatureUsageLogRepository usageLogRepository;
  private final PaymentRepository paymentRepository;
  private final OrganizationRepository organizationRepository;
  private final UserRepository userRepository;
  private final SubscriptionsMapper mapper;
  private final AuditLogsService auditLogsService;
  private final NotificationsService notificationsService;
  private final SettingsService settingsService;

  public SubscriptionsService(
      SubscriptionPlanRepository planRepository,
      FeatureRepository featureRepository,
      InvoiceRepository invoiceRepository,
      OrganizationSubscriptionRepository orgSubRepository,
      FeatureUsageLogRepository usageLogRepository,
      PaymentRepository paymentRepository,
      OrganizationRepository organizationRepository,
      UserRepository userRepository,
      SubscriptionsMapper mapper,
      AuditLogsService auditLogsService,
      NotificationsService notificationsService,
      SettingsService settingsService) {
    this.planRepository = planRepository;
    this.featureRepository = featureRepository;
    this.invoiceRepository = invoiceRepository;
    this.orgSubRepository = orgSubRepository;
    this.usageLogRepository = usageLogRepository;
    this.paymentRepository = paymentRepository;
    this.organizationRepository = organizationRepository;
    this.userRepository = userRepository;
    this.mapper = mapper;
    this.auditLogsService = auditLogsService;
    this.notificationsService = notificationsService;
    this.settingsService = settingsService;
  }

  @Transactional(readOnly = true)
  public Organization getOrganizationByUserId(Integer userId) {
    User user = userRepository.findById(userId).orElse(null);
    if (user == null || user.getOrganization() == null) {
      throw ApiException.notFound("Organization not found for user");
    }
    // Load through the repository (not the lazy proxy off `user`) so the returned entity is
    // fully populated: callers commonly use it after this read-only transaction has closed
    // (e.g. PaymentController), and an uninitialized proxy would throw
    // LazyInitializationException at that point since open-in-view is disabled.
    return organizationRepository.findById(user.getOrganization().getId()).orElseThrow(
        () -> ApiException.notFound("Organization not found for user"));
  }

  // ---- Subscription Plans ----

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAllPlans() {
    return planRepository.findByActiveTrueOrderByPricePerMonthAsc().stream().map(mapper::toPlanMap).toList();
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAllFeatures() {
    return featureRepository.findAllByOrderByFeatureNameAsc().stream().map(mapper::toFeatureMap).toList();
  }

  @Transactional
  public Map<String, Object> createFeature(FeatureRequest data) {
    Feature feature = new Feature();
    applyFeatureFields(feature, data);
    Feature saved = featureRepository.save(feature);
    auditLogsService.logAction(null, "CREATE_FEATURE", "Created feature: " + saved.getFeatureName());
    return mapper.toFeatureMap(saved);
  }

  @Transactional
  public Map<String, Object> updateFeature(Integer id, FeatureRequest data) {
    Feature feature = featureRepository.findById(id).orElseThrow(() -> ApiException.notFound("Feature not found"));
    applyFeatureFields(feature, data);
    Feature saved = featureRepository.save(feature);
    auditLogsService.logAction(null, "UPDATE_FEATURE", "Updated feature: " + saved.getFeatureName());
    return mapper.toFeatureMap(saved);
  }

  private void applyFeatureFields(Feature feature, FeatureRequest data) {
    if (data.feature_code != null) feature.setFeatureCode(data.feature_code);
    if (data.feature_name != null) feature.setFeatureName(data.feature_name);
    if (data.description != null) feature.setDescription(data.description);
  }

  @Transactional
  public void removeFeature(Integer id) {
    Feature feature = featureRepository.findById(id).orElse(null);
    featureRepository.deleteById(id);
    if (feature != null) {
      auditLogsService.logAction(null, "DELETE_FEATURE", "Deleted feature: " + feature.getFeatureName());
    }
  }

  @Transactional
  public Map<String, Object> createPlan(PlanRequest data) {
    SubscriptionPlan plan = new SubscriptionPlan();
    applyPlanFields(plan, data);
    SubscriptionPlan saved = planRepository.save(plan);
    auditLogsService.logAction(null, "CREATE_PLAN", "Created subscription plan: " + saved.getPlanName());
    return mapper.toPlanMap(saved);
  }

  @Transactional
  public Map<String, Object> updatePlan(Integer id, PlanRequest data) {
    SubscriptionPlan plan = planRepository.findById(id).orElseThrow(() -> ApiException.notFound("Plan not found"));
    applyPlanFields(plan, data);
    SubscriptionPlan saved = planRepository.save(plan);
    auditLogsService.logAction(null, "UPDATE_PLAN", "Updated plan: " + saved.getPlanName());
    return mapper.toPlanMap(saved);
  }

  private void applyPlanFields(SubscriptionPlan plan, PlanRequest data) {
    if (data.plan_name != null) plan.setPlanName(data.plan_name);
    if (data.description != null) plan.setDescription(data.description);
    if (data.badge != null) plan.setBadge(data.badge);
    if (data.price_per_month != null) plan.setPricePerMonth(data.price_per_month);
    if (data.stripe_price_id != null) plan.setStripePriceId(data.stripe_price_id);
    if (data.max_users != null) plan.setMaxUsers(data.max_users);
    if (data.max_locations != null) plan.setMaxLocations(data.max_locations);
    if (data.is_active != null) plan.setActive(data.is_active);
    if (data.feature_ids != null) {
      plan.setFeatures(featureRepository.findByIdIn(data.feature_ids));
    }
  }

  @Transactional
  public void removePlan(Integer id) {
    SubscriptionPlan plan = planRepository.findById(id).orElse(null);
    planRepository.deleteById(id);
    if (plan != null) {
      auditLogsService.logAction(null, "DELETE_PLAN", "Deleted plan: " + plan.getPlanName());
    }
  }

  // ---- Invoices ----

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAllInvoices(Integer page, Integer limit) {
    int safeLimit = limit != null ? Math.min(Math.max(1, limit), 200) : 100;
    int safePage = page != null ? Math.max(1, page) : 1;
    Pageable pageable = PageRequest.of(safePage - 1, safeLimit);
    return invoiceRepository.findAllByOrderByCreatedAtDesc(pageable).stream().map(mapper::toInvoiceMap).toList();
  }

  @Transactional
  public Map<String, Object> updateInvoiceStatus(Integer id, String status) {
    Invoice invoice = invoiceRepository.findById(id).orElseThrow(() -> ApiException.notFound("Invoice not found"));
    invoice.setStatus(status);
    Invoice updated = invoiceRepository.save(invoice);

    auditLogsService.logAction(
        null, "UPDATE_INVOICE", "Updated invoice " + invoice.getReferenceNumber() + " status to " + status);

    if ("PAID".equals(status)) {
      recordPaymentForInvoice(id, "PAID");
    }

    if ("PAID".equals(status) && updated.getOrganization() != null) {
      userRepository.search(updated.getOrganization().getId(), null, PageRequest.of(0, 1)).stream()
          .findFirst()
          .ifPresent(
              user ->
                  notificationsService.create(
                      "ชำระเงินเรียบร้อยแล้ว",
                      "ใบแจ้งหนี้เลขที่ "
                          + invoice.getReferenceNumber()
                          + " สำหรับแพ็กเกจ "
                          + (updated.getPlan() != null ? updated.getPlan().getPlanName() : "-")
                          + " ได้รับการยืนยันแล้ว",
                      NotificationType.SYSTEM,
                      user.getId(),
                      null,
                      "/org/subscriptions"));
    }

    return mapper.toInvoiceMap(updated);
  }

  // ---- Feature Access / Quotas ----

  @Transactional(readOnly = true)
  public OrganizationSubscription findOrgSubscriptionEntity(Integer orgId) {
    return orgSubRepository.findByOrgIdAndStatus(orgId, "ACTIVE").orElse(null);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> findOrgSubscription(Integer orgId) {
    OrganizationSubscription sub = findOrgSubscriptionEntity(orgId);
    return sub != null ? mapper.toOrgSubscriptionMap(sub) : null;
  }

  @Transactional(readOnly = true)
  public boolean canAccessFeature(Integer orgId, String featureCode) {
    OrganizationSubscription sub = findOrgSubscriptionEntity(orgId);
    if (sub == null || sub.getPlan() == null) return false;
    return sub.getPlan().getFeatures().stream()
        .anyMatch(f -> f.getFeatureCode() != null && f.getFeatureCode().equalsIgnoreCase(featureCode));
  }

  @Transactional(readOnly = true)
  public int getQuotaLimit(Integer planId, String featureCode) {
    String key = "quota.plan:" + planId + ".feat:" + featureCode.toLowerCase();
    Object value = settingsService.getSetting(key);
    if (value != null) {
      try {
        return (int) Double.parseDouble(String.valueOf(value));
      } catch (NumberFormatException ignored) {
        // fall through to unlimited default
      }
    }
    return 0;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> checkFeatureQuota(Integer orgId, String featureCode) {
    OrganizationSubscription sub = findOrgSubscriptionEntity(orgId);
    if (sub == null || sub.getPlan() == null) {
      return Map.of("allowed", false, "used", 0, "limit", 0);
    }
    int limit = getQuotaLimit(sub.getPlan().getId(), featureCode);
    Instant now = Instant.now();
    java.time.ZonedDateTime zdt = now.atZone(java.time.ZoneId.systemDefault());
    int used =
        usageLogRepository
            .findByOrgIdAndFeatureCodeAndUsageMonthAndUsageYear(
                orgId, featureCode.toUpperCase(), zdt.getMonthValue(), zdt.getYear())
            .map(FeatureUsageLog::getUsageCount)
            .orElse(0);
    boolean allowed = limit == 0 || limit >= 999999 || used < limit;
    return Map.of("allowed", allowed, "used", used, "limit", limit);
  }

  @Transactional
  public void logFeatureUsage(Integer orgId, String featureCode, int amount) {
    java.time.ZonedDateTime zdt = Instant.now().atZone(java.time.ZoneId.systemDefault());
    int month = zdt.getMonthValue();
    int year = zdt.getYear();
    FeatureUsageLog log =
        usageLogRepository
            .findByOrgIdAndFeatureCodeAndUsageMonthAndUsageYear(orgId, featureCode.toUpperCase(), month, year)
            .orElseGet(
                () -> {
                  FeatureUsageLog l = new FeatureUsageLog();
                  l.setOrgId(orgId);
                  l.setFeatureCode(featureCode.toUpperCase());
                  l.setUsageCount(0);
                  l.setUsageMonth(month);
                  l.setUsageYear(year);
                  return l;
                });
    log.setUsageCount(log.getUsageCount() + amount);
    usageLogRepository.save(log);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> getFeatureUsageLogs(Integer orgId, Integer month, Integer year) {
    java.time.ZonedDateTime zdt = Instant.now().atZone(java.time.ZoneId.systemDefault());
    int m = month != null ? month : zdt.getMonthValue();
    int y = year != null ? year : zdt.getYear();
    return usageLogRepository.findByOrgIdAndUsageMonthAndUsageYearOrderByFeatureCodeAsc(orgId, m, y).stream()
        .map(mapper::toUsageLogMap)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> getOrganizationFeatureQuotaSummary(Integer orgId) {
    OrganizationSubscription sub = findOrgSubscriptionEntity(orgId);
    if (sub == null || sub.getPlan() == null || sub.getPlan().getFeatures().isEmpty()) {
      return List.of();
    }
    java.time.ZonedDateTime zdt = Instant.now().atZone(java.time.ZoneId.systemDefault());
    int month = zdt.getMonthValue();
    int year = zdt.getYear();
    List<FeatureUsageLog> logs =
        usageLogRepository.findByOrgIdAndUsageMonthAndUsageYearOrderByFeatureCodeAsc(orgId, month, year);

    return sub.getPlan().getFeatures().stream()
        .map(
            feature -> {
              int limit = getQuotaLimit(sub.getPlan().getId(), feature.getFeatureCode());
              int used =
                  logs.stream()
                      .filter(l -> l.getFeatureCode().equalsIgnoreCase(feature.getFeatureCode()))
                      .findFirst()
                      .map(FeatureUsageLog::getUsageCount)
                      .orElse(0);
              return Map.<String, Object>of(
                  "feature_code", feature.getFeatureCode().toUpperCase(),
                  "feature_name", feature.getFeatureName(),
                  "used", used,
                  "limit", limit,
                  "allowed", limit == 0 || limit >= 999999 || used < limit);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> getOrganizationPayments(Integer orgId) {
    return paymentRepository.findByOrgIdOrderByPaidAtDescCreatedAtDesc(orgId).stream()
        .map(mapper::toPaymentMap)
        .toList();
  }

  @Transactional
  public Payment createPaymentRecord(
      Integer invoiceId, Integer orgId, Double amount, String currency, String method, String status, Instant paidAt) {
    Payment payment = new Payment();
    // invoice_id/org_id are insertable=false shadow columns (mirrors the same fix already
    // applied to OrganizationSubscription/Organization elsewhere) — the association objects
    // must be set instead, or the FK columns are silently left null on INSERT.
    if (invoiceId != null) {
      Invoice invoiceRef = new Invoice();
      invoiceRef.setId(invoiceId);
      payment.setInvoice(invoiceRef);
    }
    if (orgId != null) {
      Organization orgRef = new Organization();
      orgRef.setId(orgId);
      payment.setOrganization(orgRef);
    }
    payment.setAmount(amount);
    payment.setCurrency(currency);
    payment.setPaymentMethod(method);
    payment.setPaymentStatus(status);
    payment.setPaidAt(paidAt);
    return paymentRepository.save(payment);
  }

  @Transactional
  public void recordPaymentForInvoice(Integer invoiceId, String status) {
    Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> ApiException.notFound("Invoice not found"));
    Instant paidAt = "PAID".equals(status) ? Instant.now() : null;

    paymentRepository
        .findByInvoiceId(invoiceId)
        .ifPresentOrElse(
            existing -> {
              existing.setAmount(invoice.getAmount() != null ? invoice.getAmount() : 0);
              existing.setPaymentStatus(status);
              existing.setPaidAt(paidAt);
              paymentRepository.save(existing);
            },
            () ->
                createPaymentRecord(
                    invoiceId,
                    invoice.getOrgId(),
                    invoice.getAmount() != null ? invoice.getAmount() : 0,
                    "THB",
                    "stripe",
                    status,
                    paidAt));
  }

  @Transactional
  public Map<String, Object> subscribeToPlan(Integer orgId, Integer planId) {
    SubscriptionPlan plan = planRepository.findById(planId).orElseThrow(() -> ApiException.badRequest("Plan not found"));
    if (plan.getPricePerMonth() != null && plan.getPricePerMonth() > 0) {
      throw ApiException.badRequest("Cannot subscribe directly to a paid plan without payment info");
    }

    orgSubRepository
        .findByOrgIdAndStatus(orgId, "ACTIVE")
        .ifPresent(
            existing -> {
              existing.setStatus("CANCELLED");
              orgSubRepository.save(existing);
            });

    OrganizationSubscription newSub = new OrganizationSubscription();
    Organization org = new Organization();
    org.setId(orgId);
    newSub.setOrganization(org);
    newSub.setPlan(plan);
    newSub.setStatus("ACTIVE");
    newSub.setStartDate(LocalDate.now());
    newSub.setEndDate(LocalDate.now().plusYears(10));
    newSub.setAutoRenew(true);
    OrganizationSubscription saved = orgSubRepository.save(newSub);

    auditLogsService.logAction(
        null, "SUBSCRIBE_PLAN", "Organization " + orgId + " subscribed to free plan: " + plan.getPlanName());

    return Map.of(
        "success", true,
        "message", "Subscribed to plan successfully",
        "subscription", mapper.toOrgSubscriptionMap(saved));
  }

  public Map<String, Object> subscribeToPaidPlan(Integer orgId, Integer planId, String paymentMethodId) {
    throw new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE, "การชำระเงินผ่าน Stripe ยังไม่พร้อมใช้งาน (ยังไม่ได้ตั้งค่า Stripe API key)");
  }

  @Transactional
  public Map<String, Object> cancelSubscription(Integer orgId) {
    OrganizationSubscription sub =
        orgSubRepository.findByOrgIdAndStatus(orgId, "ACTIVE").orElseThrow(() -> ApiException.notFound("No active subscription found"));

    sub.setAutoRenew(false);
    sub.setStatus("CANCELLED");
    orgSubRepository.save(sub);

    auditLogsService.logAction(null, "CANCEL_SUBSCRIPTION", "Organization " + orgId + " cancelled their subscription");

    return Map.of("success", true, "message", "Subscription cancelled successfully");
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getUserSubscriptionStatusByUserId(Integer userId) {
    Organization org = getOrganizationByUserId(userId);
    OrganizationSubscription sub = findOrgSubscriptionEntity(org.getId());
    List<Map<String, Object>> quotas = getOrganizationFeatureQuotaSummary(org.getId());

    Map<String, Object> aiQuota =
        quotas.stream()
            .filter(q -> "AI_SCAN".equals(q.get("feature_code")))
            .findFirst()
            .orElse(Map.of("used", 0, "limit", 0));

    java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("planName", sub != null && sub.getPlan() != null ? sub.getPlan().getPlanName() : "Free Plan");
    result.put("aiScanLimit", aiQuota.get("limit"));
    result.put("aiScanUsed", aiQuota.get("used"));
    result.put("expiryDate", sub != null ? sub.getEndDate() : null);
    return result;
  }
}
