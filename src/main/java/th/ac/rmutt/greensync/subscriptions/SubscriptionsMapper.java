package th.ac.rmutt.greensync.subscriptions;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionsMapper {

  public Map<String, Object> toFeatureMap(Feature f) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", f.getId());
    map.put("feature_code", f.getFeatureCode());
    map.put("feature_name", f.getFeatureName());
    map.put("description", f.getDescription());
    map.put("created_at", f.getCreatedAt());
    return map;
  }

  public Map<String, Object> toPlanMap(SubscriptionPlan p) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", p.getId());
    map.put("plan_name", p.getPlanName());
    map.put("description", p.getDescription());
    map.put("badge", p.getBadge());
    map.put("price_per_month", p.getPricePerMonth());
    map.put("stripe_price_id", p.getStripePriceId());
    map.put("max_users", p.getMaxUsers());
    map.put("max_locations", p.getMaxLocations());
    map.put("is_active", p.isActive());
    map.put("features", p.getFeatures().stream().map(this::toFeatureMap).toList());
    map.put("created_at", p.getCreatedAt());
    return map;
  }

  public Map<String, Object> toInvoiceMap(Invoice i) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", i.getId());
    map.put("org_id", i.getOrgId());
    map.put("plan_id", i.getPlanId());
    map.put("amount", i.getAmount());
    map.put("status", i.getStatus());
    map.put("reference_number", i.getReferenceNumber());
    map.put("notes", i.getNotes());
    map.put("created_at", i.getCreatedAt());
    if (i.getOrganization() != null) {
      Map<String, Object> org = new LinkedHashMap<>();
      org.put("id", i.getOrganization().getId());
      org.put("name", i.getOrganization().getName());
      map.put("organization", org);
    } else {
      map.put("organization", null);
    }
    map.put("plan", i.getPlan() != null ? toPlanMap(i.getPlan()) : null);
    return map;
  }

  public Map<String, Object> toOrgSubscriptionMap(OrganizationSubscription s) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", s.getId());
    map.put("org_id", s.getOrganization() != null ? s.getOrganization().getId() : s.getOrgId());
    map.put("plan_id", s.getPlan() != null ? s.getPlan().getId() : s.getPlanId());
    map.put("start_date", s.getStartDate());
    map.put("end_date", s.getEndDate());
    map.put("status", s.getStatus());
    map.put("auto_renew", s.isAutoRenew());
    map.put("created_at", s.getCreatedAt());
    map.put("plan", s.getPlan() != null ? toPlanMap(s.getPlan()) : null);
    return map;
  }

  public Map<String, Object> toPaymentMap(Payment p) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", p.getId());
    map.put("invoice_id", p.getInvoice() != null ? p.getInvoice().getId() : p.getInvoiceId());
    map.put("org_id", p.getOrganization() != null ? p.getOrganization().getId() : p.getOrgId());
    map.put("amount", p.getAmount());
    map.put("currency", p.getCurrency());
    map.put("payment_method", p.getPaymentMethod());
    map.put("payment_status", p.getPaymentStatus());
    map.put("paid_at", p.getPaidAt());
    map.put("created_at", p.getCreatedAt());
    return map;
  }

  public Map<String, Object> toUsageLogMap(FeatureUsageLog l) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", l.getId());
    map.put("org_id", l.getOrgId());
    map.put("feature_code", l.getFeatureCode());
    map.put("usage_count", l.getUsageCount());
    map.put("usage_date", l.getUsageDate());
    map.put("usage_month", l.getUsageMonth());
    map.put("usage_year", l.getUsageYear());
    map.put("created_at", l.getCreatedAt());
    return map;
  }
}
