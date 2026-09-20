package th.ac.rmutt.greensync.carbonlogs.dto;

/** Used for both create and update, same as the NestJS Create/Update DTO pair. */
public class CarbonLogRequest {
  public String activity_type;
  public Integer month;
  public Integer year;
  public Double usage_amount;
  public Double total_emission;
  public Integer emission_factor_id;
  public String evidence_url;
  public String data_source;
  public Integer org_unit_id;
}
