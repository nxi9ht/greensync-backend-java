package th.ac.rmutt.greensync.notifications.dto;

import java.util.Map;

public class ProposeAcademicChangeRequest {
  public String targetType;
  public Integer targetId;
  public String name;
  public String oldValue;
  public String newValue;
  public String reason;
  public Map<String, Object> details;
}
