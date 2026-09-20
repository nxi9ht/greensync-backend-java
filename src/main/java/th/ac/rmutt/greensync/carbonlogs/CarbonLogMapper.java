package th.ac.rmutt.greensync.carbonlogs;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CarbonLogMapper {

  public Map<String, Object> toMap(CarbonLog log) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", log.getId());
    map.put("emission_factor_id", log.getEmissionFactor() != null ? log.getEmissionFactor().getId() : null);
    map.put("org_id", log.getOrganization() != null ? log.getOrganization().getId() : null);
    map.put("org_unit_id", log.getOrganizationUnit() != null ? log.getOrganizationUnit().getId() : null);
    map.put("activity_type", log.getActivityType());
    map.put("month", log.getMonth());
    map.put("year", log.getYear());
    map.put("usage_amount", log.getUsageAmount());
    map.put("total_emission", log.getTotalEmission());
    map.put("evidence_url", log.getEvidenceUrl());
    map.put("data_source", log.getDataSource());
    map.put("created_at", log.getCreatedAt());
    map.put("updated_at", log.getUpdatedAt());
    map.put("emission_factor", log.getEmissionFactor() != null ? toFactorMap(log.getEmissionFactor()) : null);
    return map;
  }

  public Map<String, Object> toFactorMap(EmissionFactor factor) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", factor.getId());
    map.put("name", factor.getName());
    map.put("scope", factor.getScope());
    map.put("unit", factor.getUnit());
    map.put("factor_value", factor.getFactorValue());
    map.put("year", factor.getYear());
    map.put("source", factor.getSource());
    map.put("created_at", factor.getCreatedAt());
    return map;
  }
}
