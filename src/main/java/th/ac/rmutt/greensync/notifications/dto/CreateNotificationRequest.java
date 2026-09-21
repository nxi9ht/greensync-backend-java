package th.ac.rmutt.greensync.notifications.dto;

import th.ac.rmutt.greensync.notifications.NotificationType;

public class CreateNotificationRequest {
  public String title;
  public String message;
  public NotificationType type;
  public Integer recipient_id;
  public String link;
}
