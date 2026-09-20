package th.ac.rmutt.greensync.organizations.dto;

import jakarta.validation.constraints.Pattern;

/** Used for both create and update — NestJS's UpdateOrganizationDto is a PartialType of create. */
public class OrganizationRequest {
  public String name;

  @Pattern(regexp = "^[0-9]{13}$", message = "เลขประจำตัวผู้เสียภาษีต้องเป็นตัวเลข 13 หลัก")
  public String tax_id;

  public String industry_type;
  public Integer number_of_employees;
  public Double total_floor_area;
  public Integer working_hours_per_year;
  public Integer base_year;
  public Double target_reduction_percent;
  public Integer target_year;
  public Double industry_benchmark_value;
  public String carbon_standard;
  public String current_green_status;
  public Boolean is_active;
}
