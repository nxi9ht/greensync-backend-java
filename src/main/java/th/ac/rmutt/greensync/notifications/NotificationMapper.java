package th.ac.rmutt.greensync.notifications;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

  public Map<String, Object> toMap(Notification n) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", n.getId());
    map.put("title", n.getTitle());
    map.put("message", n.getMessage());
    map.put("type", n.getType().name());
    map.put("is_read", n.isRead());
    map.put("link", n.getLink());
    map.put("created_at", n.getCreatedAt());
    map.put("recipient_id", n.getRecipient() != null ? n.getRecipient().getId() : null);
    map.put("sender_id", n.getSender() != null ? n.getSender().getId() : null);
    return map;
  }
}
