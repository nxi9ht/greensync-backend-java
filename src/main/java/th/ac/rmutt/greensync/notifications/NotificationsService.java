package th.ac.rmutt.greensync.notifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.assessments.GreenCriteriaService;
import th.ac.rmutt.greensync.assessments.dto.GreenCriteriaRequest;
import th.ac.rmutt.greensync.carbonlogs.EmissionFactorsService;
import th.ac.rmutt.greensync.carbonlogs.dto.EmissionFactorRequest;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.notifications.dto.CreateBulkNotificationRequest;
import th.ac.rmutt.greensync.notifications.dto.CreateNotificationRequest;
import th.ac.rmutt.greensync.notifications.dto.ProposeAcademicChangeRequest;
import th.ac.rmutt.greensync.users.User;
import th.ac.rmutt.greensync.users.UserRepository;
import th.ac.rmutt.greensync.users.UsersService;

@Service
public class NotificationsService {

  private static final Logger log = LoggerFactory.getLogger(NotificationsService.class);
  private static final Pattern SCRIPT_TAG = Pattern.compile("<script\\b[^<]*(?:(?!</script>)<[^<]*)*</script>", Pattern.CASE_INSENSITIVE);
  private static final Pattern IFRAME_TAG = Pattern.compile("<iframe\\b[^<]*(?:(?!</iframe>)<[^<]*)*</iframe>", Pattern.CASE_INSENSITIVE);
  private static final Pattern ON_ATTR_DQ = Pattern.compile("on\\w+\\s*=\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern ON_ATTR_SQ = Pattern.compile("on\\w+\\s*=\\s*'[^']*'", Pattern.CASE_INSENSITIVE);
  private static final Pattern JS_PROTOCOL = Pattern.compile("javascript\\s*:\\s*", Pattern.CASE_INSENSITIVE);

  private final NotificationRepository notificationRepository;
  private final NotificationMapper notificationMapper;
  private final MailService mailService;
  private final UsersService usersService;
  private final UserRepository userRepository;
  private final EmissionFactorsService emissionFactorsService;
  private final GreenCriteriaService greenCriteriaService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public NotificationsService(
      NotificationRepository notificationRepository,
      NotificationMapper notificationMapper,
      MailService mailService,
      UsersService usersService,
      UserRepository userRepository,
      EmissionFactorsService emissionFactorsService,
      GreenCriteriaService greenCriteriaService) {
    this.notificationRepository = notificationRepository;
    this.notificationMapper = notificationMapper;
    this.mailService = mailService;
    this.usersService = usersService;
    this.userRepository = userRepository;
    this.emissionFactorsService = emissionFactorsService;
    this.greenCriteriaService = greenCriteriaService;
  }

  private Integer findSystemAdminId() {
    for (String role : List.of("System Admin", "SYSTEM_ADMIN", "ADMIN")) {
      List<Map<String, Object>> found = usersService.findAll(role, null, null, 1);
      if (!found.isEmpty()) return (Integer) found.get(0).get("id");
    }
    List<Map<String, Object>> all = usersService.findAll(null, null, null, 1);
    return all.isEmpty() ? null : (Integer) all.get(0).get("id");
  }

  private String sanitizeText(String text) {
    if (text == null) return "";
    String result = SCRIPT_TAG.matcher(text).replaceAll("");
    result = IFRAME_TAG.matcher(result).replaceAll("");
    result = ON_ATTR_DQ.matcher(result).replaceAll("");
    result = ON_ATTR_SQ.matcher(result).replaceAll("");
    result = JS_PROTOCOL.matcher(result).replaceAll("");
    return result;
  }

  private boolean isJson(String str) {
    if (str == null) return false;
    try {
      objectMapper.readTree(str);
      return true;
    } catch (JsonProcessingException e) {
      return false;
    }
  }

  @Transactional
  public Map<String, Object> create(
      String title, String message, NotificationType type, Integer recipientId, Integer senderId, String link) {
    Integer finalRecipientId = recipientId;
    boolean recipientExists = recipientId != null && userRepository.findById(recipientId).isPresent();

    if (!recipientExists) {
      Integer adminId = findSystemAdminId();
      finalRecipientId = adminId != null ? adminId : senderId;
    }
    if (finalRecipientId == null) {
      throw ApiException.badRequest("ไม่พบผู้รับการแจ้งเตือน");
    }

    String sanitizedTitle = sanitizeText(title);
    String sanitizedMessage = isJson(message) ? message : sanitizeText(message);

    Notification notification = new Notification();
    notification.setTitle(sanitizedTitle);
    notification.setMessage(sanitizedMessage);
    notification.setType(type != null ? type : NotificationType.SYSTEM);
    notification.setLink(link);
    User recipient = new User();
    recipient.setId(finalRecipientId);
    notification.setRecipient(recipient);
    if (senderId != null) {
      User sender = new User();
      sender.setId(senderId);
      notification.setSender(sender);
    }

    Notification saved = notificationRepository.save(notification);

    userRepository
        .findById(finalRecipientId)
        .ifPresent(
            user -> {
              if (user.getEmail() != null) {
                try {
                  mailService.sendMail(
                      user.getEmail(),
                      "แจ้งเตือนระบบ: " + sanitizedTitle,
                      "<h3>" + sanitizedTitle + "</h3><p>" + sanitizedMessage + "</p>"
                          + (link != null ? "<a href=\"" + link + "\">คลิกเพื่อดูรายละเอียด</a>" : ""));
                } catch (Exception e) {
                  log.error("Error sending email notification: {}", e.getMessage());
                }
              }
            });

    return notificationMapper.toMap(saved);
  }

  @Transactional
  public List<Map<String, Object>> createBulk(
      String title, String message, NotificationType type, List<Integer> recipientIds, Integer senderId, String link) {
    String sanitizedTitle = sanitizeText(title);
    String sanitizedMessage = isJson(message) ? message : sanitizeText(message);

    List<Notification> notifications =
        recipientIds.stream()
            .map(
                id -> {
                  Notification n = new Notification();
                  n.setTitle(sanitizedTitle);
                  n.setMessage(sanitizedMessage);
                  n.setType(type != null ? type : NotificationType.SYSTEM);
                  n.setLink(link);
                  User recipient = new User();
                  recipient.setId(id);
                  n.setRecipient(recipient);
                  if (senderId != null) {
                    User sender = new User();
                    sender.setId(senderId);
                    n.setSender(sender);
                  }
                  return n;
                })
            .toList();

    List<Notification> saved = notificationRepository.saveAll(notifications);

    for (Integer id : recipientIds) {
      userRepository
          .findById(id)
          .ifPresent(
              user -> {
                if (user.getEmail() != null) {
                  try {
                    mailService.sendMail(
                        user.getEmail(),
                        "แจ้งเตือนระบบ: " + sanitizedTitle,
                        "<h3>" + sanitizedTitle + "</h3><p>" + sanitizedMessage + "</p>"
                            + (link != null ? "<a href=\"" + link + "\">คลิกเพื่อดูรายละเอียด</a>" : ""));
                  } catch (Exception e) {
                    log.error("Error sending bulk email to user {}: {}", id, e.getMessage());
                  }
                }
              });
    }

    return saved.stream().map(notificationMapper::toMap).toList();
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAllForUser(Integer userId, Integer page, Integer limit) {
    int safeLimit = limit != null ? Math.min(Math.max(1, limit), 200) : 50;
    int safePage = page != null ? Math.max(1, page) : 1;
    Pageable pageable = PageRequest.of(safePage - 1, safeLimit);
    return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable).stream()
        .map(notificationMapper::toMap)
        .toList();
  }

  @Transactional(readOnly = true)
  public long getUnreadCount(Integer userId) {
    return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
  }

  @Transactional
  public Map<String, Object> markAsRead(Integer id, Integer userId) {
    Notification notification =
        notificationRepository
            .findByIdAndRecipientId(id, userId)
            .orElseThrow(() -> ApiException.notFound("Notification not found"));
    notification.setRead(true);
    return notificationMapper.toMap(notificationRepository.save(notification));
  }

  @Transactional
  public void remove(Integer id) {
    if (!notificationRepository.existsById(id)) {
      throw ApiException.notFound("Notification not found");
    }
    notificationRepository.deleteById(id);
  }

  @Transactional
  public void markAllAsRead(Integer userId) {
    notificationRepository.markAllAsReadForRecipient(userId);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAllSystemWide(Integer page, Integer limit) {
    int safeLimit = limit != null ? Math.min(Math.max(1, limit), 200) : 50;
    int safePage = page != null ? Math.max(1, page) : 1;
    Pageable pageable = PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
    return notificationRepository.findAllOrderByCreatedAtDesc(pageable).stream()
        .map(notificationMapper::toMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> proposeAcademicChange(Integer userId, ProposeAcademicChangeRequest data) {
    Integer adminId = findSystemAdminId();
    Integer recipientId = adminId != null ? adminId : userId;

    String title =
        "คำเสนอวิชาการ: " + ("CRITERIA".equals(data.targetType) ? "เกณฑ์สำนักงานสีเขียว" : "สูตรคำนวณคาร์บอน");

    Map<String, Object> messageObj = new LinkedHashMap<>();
    messageObj.put("targetType", data.targetType);
    messageObj.put("targetId", data.targetId);
    messageObj.put("name", data.name);
    messageObj.put("oldValue", data.oldValue);
    messageObj.put("newValue", data.newValue);
    messageObj.put("reason", data.reason);
    messageObj.put("status", "PENDING");
    messageObj.put("details", data.details);

    String messageJson;
    try {
      messageJson = objectMapper.writeValueAsString(messageObj);
    } catch (JsonProcessingException e) {
      throw ApiException.badRequest("ไม่สามารถบันทึกข้อเสนอได้");
    }

    return create(title, messageJson, NotificationType.REQUEST, recipientId, userId, "/admin/approvals");
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public Map<String, Object> approveAcademicChange(Integer notificationId, Integer adminId) {
    Notification notification =
        notificationRepository.findById(notificationId).orElseThrow(() -> ApiException.notFound("คำขอไม่พบในระบบ"));

    Map<String, Object> payload = readPayload(notification.getMessage());

    if (!"PENDING".equals(payload.get("status"))) {
      return Map.of("success", false, "message", "คำขอนี้ได้รับการประมวลผลไปแล้ว");
    }

    String targetType = (String) payload.get("targetType");
    Object targetIdRaw = payload.get("targetId");
    boolean isNew =
        targetIdRaw == null || Integer.valueOf(0).equals(targetIdRaw) || "0".equals(String.valueOf(targetIdRaw));
    String name = (String) payload.getOrDefault("name", "");
    String newValue = String.valueOf(payload.getOrDefault("newValue", "0"));
    Map<String, Object> details = (Map<String, Object>) payload.get("details");
    if (details == null) details = Map.of();

    if ("CRITERIA".equals(targetType)) {
      if (isNew) {
        GreenCriteriaRequest req = new GreenCriteriaRequest();
        req.category_number = toInt(details.get("category_number"), 1);
        req.criteria_code = String.valueOf(details.getOrDefault("criteria_code", ""));
        req.criteria_name = name;
        req.max_score = parseDouble(newValue);
        req.description = String.valueOf(details.getOrDefault("description", ""));
        req.year_version = toInt(details.get("year_version"), Year.now().getValue());
        greenCriteriaService.create(req);
      } else {
        GreenCriteriaRequest req = new GreenCriteriaRequest();
        req.max_score = parseDouble(newValue);
        greenCriteriaService.update(toInt(targetIdRaw, 0), req);
      }
    } else if ("EMISSION_FACTOR".equals(targetType)) {
      if (isNew) {
        EmissionFactorRequest req = new EmissionFactorRequest();
        req.scope = toInt(details.get("scope"), 1);
        req.name = name;
        req.unit = String.valueOf(details.getOrDefault("unit", "kWh"));
        req.factor_value = parseDouble(newValue);
        req.source = String.valueOf(details.getOrDefault("source", "TGO"));
        req.year = toInt(details.get("year"), Year.now().getValue());
        emissionFactorsService.create(req);
      } else {
        EmissionFactorRequest req = new EmissionFactorRequest();
        req.factor_value = parseDouble(newValue);
        emissionFactorsService.update(toInt(targetIdRaw, 0), req);
      }
    }

    payload.put("status", "APPROVED");
    writePayload(notification, payload);
    notification.setRead(true);
    notificationRepository.save(notification);

    create(
        "ข้อเสนอวิชาการของคุณได้รับการอนุมัติแล้ว",
        "ข้อเสนอปรับปรุงสำหรับ \"" + name + "\" ได้รับการอนุมัติและเปิดใช้งานจริงในระบบฐานข้อมูลเรียบร้อยครับ",
        NotificationType.SYSTEM,
        notification.getSender() != null ? notification.getSender().getId() : null,
        adminId,
        null);

    return Map.of("success", true, "payload", payload);
  }

  @Transactional
  public Map<String, Object> rejectAcademicChange(Integer notificationId, Integer adminId, String rejectReason) {
    Notification notification =
        notificationRepository.findById(notificationId).orElseThrow(() -> ApiException.notFound("คำขอไม่พบในระบบ"));

    Map<String, Object> payload = readPayload(notification.getMessage());

    if (!"PENDING".equals(payload.get("status"))) {
      return Map.of("success", false, "message", "คำขอนี้ได้รับการประมวลผลไปแล้ว");
    }

    payload.put("status", "REJECTED");
    payload.put("rejectReason", rejectReason);
    writePayload(notification, payload);
    notification.setRead(true);
    notificationRepository.save(notification);

    String name = String.valueOf(payload.getOrDefault("name", ""));
    create(
        "ข้อเสนอวิชาการของคุณไม่ได้รับการอนุมัติ",
        "ข้อเสนอปรับปรุงสำหรับ \"" + name + "\" ไม่ผ่านการอนุมัติเนื่องจาก: \"" + rejectReason + "\"",
        NotificationType.SYSTEM,
        notification.getSender() != null ? notification.getSender().getId() : null,
        adminId,
        null);

    return Map.of("success", true, "payload", payload);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readPayload(String message) {
    try {
      return objectMapper.readValue(message, Map.class);
    } catch (JsonProcessingException e) {
      throw ApiException.notFound("ข้อมูลคำขอชำรุดเสียหาย");
    }
  }

  private void writePayload(Notification notification, Map<String, Object> payload) {
    try {
      notification.setMessage(objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException e) {
      throw ApiException.badRequest("ไม่สามารถบันทึกข้อมูลคำขอได้");
    }
  }

  private int toInt(Object value, int fallback) {
    if (value == null) return fallback;
    if (value instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private double parseDouble(String value) {
    try {
      return Double.parseDouble(value);
    } catch (Exception e) {
      return 0.0;
    }
  }
}
