package th.ac.rmutt.greensync.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class OrgDataRequest {
  @NotBlank public String name;

  @Pattern(regexp = "^[0-9]{13}$", message = "เลขประจำตัวผู้เสียภาษีต้องเป็นตัวเลข 13 หลัก")
  public String tax_id;

  public String industry_type;
  public Integer number_of_employees;
  public Double total_floor_area;
  public Integer working_hours_per_year;
  public Integer base_year;
  public Double target_reduction_percent;
  public Boolean is_active;
  public String current_green_status;
}
