package th.ac.rmutt.greensync.subscriptions.dto;

import java.util.List;

public class PlanRequest {
  public String plan_name;
  public String description;
  public String badge;
  public Double price_per_month;
  public String stripe_price_id;
  public Integer max_users;
  public Integer max_locations;
  public Boolean is_active;
  public List<Integer> feature_ids;
}
