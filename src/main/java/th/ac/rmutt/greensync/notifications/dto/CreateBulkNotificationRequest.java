package th.ac.rmutt.greensync.notifications.dto;

import java.util.List;
import th.ac.rmutt.greensync.notifications.NotificationType;

public class CreateBulkNotificationRequest {
  public String title;
  public String message;
  public NotificationType type;
  public List<Integer> recipient_ids;
  public String link;
}
