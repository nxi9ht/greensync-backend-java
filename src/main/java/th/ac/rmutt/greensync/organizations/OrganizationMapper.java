package th.ac.rmutt.greensync.organizations;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Flattens {@link Organization} into the same snake_case JSON shape the NestJS TypeORM entity
 * produced (its TS property names are already snake_case, e.g. {@code tax_id}), since the Java
 * entity uses idiomatic camelCase field names instead.
 */
@Component
public class OrganizationMapper {

  public Map<String, Object> toMap(Organization org) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", org.getId());
    map.put("name", org.getName());
    map.put("tax_id", org.getTaxId());
    map.put("industry_type", org.getIndustryType());
    map.put("number_of_employees", org.getNumberOfEmployees());
    map.put("total_floor_area", org.getTotalFloorArea());
    map.put("working_hours_per_year", org.getWorkingHoursPerYear());
    map.put("base_year", org.getBaseYear());
    map.put("target_reduction_percent", org.getTargetReductionPercent());
    map.put("target_year", org.getTargetYear());
    map.put("industry_benchmark_value", org.getIndustryBenchmarkValue());
    map.put("carbon_standard", org.getCarbonStandard());
    map.put("current_green_status", org.getCurrentGreenStatus());
    map.put("is_active", org.isActive());
    map.put("created_at", org.getCreatedAt());
    map.put("updated_at", org.getUpdatedAt());
    return map;
  }

  public Map<String, Object> toListItem(Organization org, int userCount) {
    Map<String, Object> map = toMap(org);
    map.put("userCount", userCount);
    return map;
  }

  public Map<String, Object> toUnitMap(OrganizationUnit unit) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", unit.getId());
    // Read straight off the association rather than the shadow org_id/parent_unit_id columns:
    // those are insertable=false/updatable=false mirrors that Hibernate only backfills on a
    // fresh read, so right after save() they're still null even though the row is correct.
    map.put("org_id", unit.getOrganization() != null ? unit.getOrganization().getId() : null);
    map.put("unit_name", unit.getUnitName());
    map.put(
        "parent_unit_id", unit.getParentUnit() != null ? unit.getParentUnit().getId() : null);
    map.put("unit_type", unit.getUnitType());
    map.put("area", unit.getArea());
    map.put("created_at", unit.getCreatedAt());
    return map;
  }
}
