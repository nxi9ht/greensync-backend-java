package th.ac.rmutt.greensync.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.assessments.Assessment;
import th.ac.rmutt.greensync.assessments.AssessmentRepository;
import th.ac.rmutt.greensync.carbonlogs.CarbonLog;
import th.ac.rmutt.greensync.carbonlogs.CarbonLogRepository;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.organizations.Organization;
import th.ac.rmutt.greensync.organizations.OrganizationRepository;
import th.ac.rmutt.greensync.organizations.OrganizationUnit;
import th.ac.rmutt.greensync.organizations.OrganizationUnitRepository;
import th.ac.rmutt.greensync.users.User;
import th.ac.rmutt.greensync.users.UserRepository;

@Service
public class GeminiService {

  private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

  private final GeminiClient geminiClient;
  private final ChatLogRepository chatLogRepository;
  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationUnitRepository organizationUnitRepository;
  private final CarbonLogRepository carbonLogRepository;
  private final AssessmentRepository assessmentRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final Map<Integer, Cooldown> orgCooldowns = new ConcurrentHashMap<>();

  private record Cooldown(Instant until, String reason) {}

  public GeminiService(
      GeminiClient geminiClient,
      ChatLogRepository chatLogRepository,
      UserRepository userRepository,
      OrganizationRepository organizationRepository,
      OrganizationUnitRepository organizationUnitRepository,
      CarbonLogRepository carbonLogRepository,
      AssessmentRepository assessmentRepository) {
    this.geminiClient = geminiClient;
    this.chatLogRepository = chatLogRepository;
    this.userRepository = userRepository;
    this.organizationRepository = organizationRepository;
    this.organizationUnitRepository = organizationUnitRepository;
    this.carbonLogRepository = carbonLogRepository;
    this.assessmentRepository = assessmentRepository;
  }

  private boolean isCooldownActive(Integer orgId) {
    Cooldown cd = orgCooldowns.get(orgId);
    if (cd == null) return false;
    if (Instant.now().isBefore(cd.until())) {
      log.warn("[AI Cache] Circuit breaker active for Org ID {} until {}. Reason: {}", orgId, cd.until(), cd.reason());
      return true;
    }
    orgCooldowns.remove(orgId);
    return false;
  }

  private void setCooldown(Integer orgId, long durationMinutes, String reason) {
    orgCooldowns.put(orgId, new Cooldown(Instant.now().plusSeconds(durationMinutes * 60), reason));
    log.info("[AI Cache] Set circuit breaker cooldown for Org ID {} for {} minutes. Reason: {}", orgId, durationMinutes, reason);
  }

  private String translateAiError(Throwable error) {
    String msg = String.valueOf(error.getMessage()).toLowerCase();
    if (msg.contains("timeout")) {
      return "ปัญญาประดิษฐ์ใช้เวลาตอบสนองนานเกินไป (Timeout) กรุณาลองใหม่อีกครั้ง";
    }
    if (msg.contains("429") || msg.contains("quota") || msg.contains("limit")) {
      return "โควตาการใช้งาน AI เต็มรูปแบบชั่วคราว กรุณารอ 1-2 นาทีแล้วลองใหม่อีกครั้ง";
    }
    if (msg.contains("api_key") || msg.contains("unauthorized") || msg.contains("key")) {
      return "ระบบเชื่อมต่อ AI ไม่ถูกต้อง (API Key มีปัญหา) กรุณาติดต่อผู้ดูแลระบบ";
    }
    return "ไม่สามารถประมวลผลผ่าน AI ได้ในขณะนี้ กรุณาลองใหม่อีกครั้ง";
  }

  private String cleanJsonResponse(String text) {
    return text.replaceFirst("(?i)^```json\\s*", "").replaceFirst("(?i)^```\\s*", "").replaceFirst("```\\s*$", "").trim();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> ocr(byte[] fileBytes, String mimeType) {
    String prompt =
        """
        You are an expert at reading Thai utility bills (electricity, water, gas, fuel).
        Analyze this bill image and extract the following information. Respond ONLY with a valid JSON object, no markdown, no explanation.

        {
          "type": "<activity type in Thai, e.g. ไฟฟ้า, น้ำประปา, ก๊าซ, น้ำมัน>",
          "amount": <numeric usage amount, numbers only>,
          "unit": "<unit in Thai, e.g. kWh, หน่วย, ลิตร, ลบ.ม.>",
          "date": "<billing month/year in Thai format, e.g. มกราคม 2568>",
          "confidence": <confidence percentage 0-100 as integer>,
          "rawText": "<brief summary of key info found on the bill>"
        }

        If you cannot determine a value, use a sensible default (0 for numbers, "ไม่ทราบ" for strings).""";
    try {
      String text = geminiClient.generateWithMedia(prompt, fileBytes, mimeType);
      return objectMapper.readValue(cleanJsonResponse(text), Map.class);
    } catch (Exception error) {
      log.error("Gemini API error: {}", error.getMessage());
      throw ApiException.badRequest(translateAiError(error));
    }
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> getSessions(Integer userId) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (ChatLogRepository.SessionSummary s : chatLogRepository.findSessionSummaries(userId)) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("id", s.getSessionId());
      map.put("title", s.getTitle());
      map.put("user_id", userId);
      map.put("created_at", s.getUpdatedAt());
      map.put("updated_at", s.getUpdatedAt());
      result.add(map);
    }
    return result;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> getSessionMessages(Integer sessionId, Integer userId) {
    List<Map<String, Object>> messages = new ArrayList<>();
    for (ChatLog entry : chatLogRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId)) {
      if (entry.getQuestion() != null) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "q-" + entry.getId());
        m.put("role", "user");
        m.put("content", entry.getQuestion());
        m.put("created_at", entry.getCreatedAt());
        messages.add(m);
      }
      if (entry.getAnswer() != null) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "a-" + entry.getId());
        m.put("role", "assistant");
        m.put("content", entry.getAnswer());
        m.put("created_at", entry.getCreatedAt());
        messages.add(m);
      }
    }
    return messages;
  }

  public Map<String, Object> createSession(Integer userId, String title) {
    long sessionId = System.currentTimeMillis() / 1000 + (long) (Math.random() * 1000);
    Instant now = Instant.now();
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", sessionId);
    map.put("title", title);
    map.put("user_id", userId);
    map.put("created_at", now);
    map.put("updated_at", now);
    return map;
  }

  @Transactional
  public void deleteSession(Integer sessionId, Integer userId) {
    chatLogRepository.deleteBySessionIdAndUserId(sessionId, userId);
  }

  @Transactional
  public Map<String, Object> chat(String message, Integer userId, Integer sessionId) {
    try {
      String orgContext = buildOrgContext(userId);

      String systemContext =
          """
          คุณคือ GreenBot ผู้ช่วย AI ของระบบ Green Sync ที่เชี่ยวชาญด้าน:
          1. การประเมินสำนักงานสีเขียว (Green Office) ตามมาตรฐานกระทรวงทรัพยากรธรรมชาติและสิ่งแวดล้อม
          2. การคำนวณและลดการปล่อยก๊าซเรือนกระจก (Carbon Footprint)
          3. เกณฑ์การประเมินสำนักงานสีเขียว (Green Office Criteria 6 หมวดหลัก)
          4. แนวทางการจัดการพลังงาน น้ำ ขยะ และสิ่งแวดล้อมในสำนักงาน

          คำสั่งสำคัญ:
          - ตอบเป็นภาษาไทยเสมอ ใช้ภาษาที่เป็นมิตรและชัดเจน
          - ตอบโดยวิเคราะห์อ้างอิงจาก "ข้อมูลสภาพแวดล้อมและพลังงานขององค์กรปัจจุบัน" ด้านล่างนี้เสมอ
          - หากผู้ใช้ถามเกี่ยวกับสถิติคาร์บอน หรือสาขาที่มี ให้ดึงจากข้อมูลสภาพแวดล้อมด้านล่างนี้ตอบผู้ใช้ทันที ห้ามบอกว่าไม่มีข้อมูลเด็ดขาด
          - หากผู้ใช้เริ่มบทสนทนาใหม่ คุณสามารถกล่าวทักทายได้
          - แต่ถ้าคุณมีประวัติการสนทนากับผู้ใช้อยู่แล้ว ห้ามกล่าวทักทายซ้ำอีกเด็ดขาด ให้ตอบคำถามตรงๆ ได้เลย
          - ถ้าคำถามไม่เกี่ยวกับหัวข้อข้างต้น ให้แจ้งว่าคุณช่วยได้เฉพาะเรื่อง Green Office และ Carbon Footprint เท่านั้น

          %s"""
              .formatted(orgContext);

      String historyContext = "";
      String sessionTitle = "New Conversation";

      if (userId != null && sessionId != null) {
        List<ChatLog> prevLogs =
            chatLogRepository.findBySessionIdAndUserIdOrderByCreatedAtDesc(sessionId, userId, PageRequest.of(0, 5));
        if (!prevLogs.isEmpty()) {
          sessionTitle = prevLogs.get(0).getSessionTitle() != null ? prevLogs.get(0).getSessionTitle() : "New Conversation";
          List<ChatLog> recent = new ArrayList<>(prevLogs);
          java.util.Collections.reverse(recent);
          StringBuilder sb = new StringBuilder("--- ประวัติการสนทนาก่อนหน้า ---\n");
          for (ChatLog m : recent) {
            sb.append("ผู้ใช้: ").append(m.getQuestion()).append("\nGreenBot: ").append(m.getAnswer()).append("\n\n");
          }
          sb.append("------------------------------\n\n");
          historyContext = sb.toString();
        }
      }

      String fullPrompt = systemContext + "\n\n" + historyContext + "ผู้ใช้: " + message + "\n\nGreenBot:";

      String reply = geminiClient.generateText(fullPrompt);
      if (reply == null || reply.isBlank()) {
        reply = "ขออภัย ไม่สามารถตอบกลับได้ในขณะนี้";
      }

      try {
        if (userId != null) {
          if (sessionId != null && "New Conversation".equals(sessionTitle)) {
            sessionTitle = message.length() > 30 ? message.substring(0, 30) + "..." : message;
          }
          ChatLog flatLog = new ChatLog();
          User u = new User();
          u.setId(userId);
          flatLog.setUser(u);
          flatLog.setQuestion(message);
          flatLog.setAnswer(reply);
          flatLog.setIntent("Chat");
          flatLog.setRelatedModule("gemini");
          flatLog.setConfidenceScore(1.0);
          flatLog.setSessionId(sessionId);
          flatLog.setSessionTitle(sessionId != null ? sessionTitle : null);
          chatLogRepository.save(flatLog);
        }
      } catch (Exception dbError) {
        log.error("Failed to save chat log: {}", dbError.getMessage());
      }

      return Map.of("reply", reply);
    } catch (Exception error) {
      log.error("Gemini chat error: {}", error.getMessage());
      throw ApiException.badRequest(translateAiError(error));
    }
  }

  private String buildOrgContext(Integer userId) {
    if (userId == null) return "";
    try {
      User user = userRepository.findById(userId).orElse(null);
      Organization org = user != null ? user.getOrganization() : null;
      if (org == null) return "";

      List<OrganizationUnit> branches = organizationUnitRepository.findByOrgIdOrderByCreatedAtAsc(org.getId());
      List<CarbonLog> carbonLogs = carbonLogRepository.findByOrganizationIdOrderByCreatedAtAsc(org.getId());

      Map<String, double[]> carbonMap = new LinkedHashMap<>();
      Map<String, String> unitMap = new LinkedHashMap<>();
      for (CarbonLog l : carbonLogs) {
        String type = l.getActivityType() != null ? l.getActivityType() : "ทั่วไป";
        String unit = l.getEmissionFactor() != null && l.getEmissionFactor().getUnit() != null ? l.getEmissionFactor().getUnit() : "หน่วย";
        double[] existing = carbonMap.computeIfAbsent(type, k -> new double[2]);
        existing[0] += l.getUsageAmount() != null ? l.getUsageAmount() : 0;
        existing[1] += l.getTotalEmission() != null ? l.getTotalEmission() : 0;
        unitMap.put(type, unit);
      }

      List<Assessment> assessments = assessmentRepository.findTop50ByOrganizationIdOrderBySubmittedAtDesc(org.getId());

      StringBuilder sb = new StringBuilder();
      sb.append("--- ข้อมูลสภาพแวดล้อมและพลังงานขององค์กรปัจจุบัน (").append(org.getName()).append(") ---\n");
      sb.append("อุตสาหกรรม: ").append(org.getIndustryType() != null ? org.getIndustryType() : "-").append("\n");
      sb.append("จำนวนพนักงาน: ").append(org.getNumberOfEmployees() != null ? org.getNumberOfEmployees() : 0).append(" คน\n");
      sb.append("พื้นที่ใช้สอยทั้งหมด: ").append(org.getTotalFloorArea() != null ? org.getTotalFloorArea() : 0).append(" ตร.ม.\n");
      sb.append("ชั่วโมงการทำงาน/ปี: ").append(org.getWorkingHoursPerYear() != null ? org.getWorkingHoursPerYear() : 0).append(" ชม.\n");
      sb.append("เป้าหมายการลดคาร์บอน: ").append(org.getTargetReductionPercent() != null ? org.getTargetReductionPercent() : 0).append("%\n\n");

      sb.append("สาขาขององค์กร (").append(branches.size()).append(" สาขา):\n");
      if (branches.isEmpty()) {
        sb.append("- ยังไม่มีข้อมูลสาขา\n");
      } else {
        for (OrganizationUnit b : branches) {
          sb.append("- สาขา ").append(b.getUnitName())
              .append(" (ประเภท: ").append(b.getUnitType() != null ? b.getUnitType() : "สำนักงาน")
              .append(", พื้นที่: ").append(b.getArea() != null ? b.getArea() : 0).append(" ตร.ม.)\n");
        }
      }

      sb.append("\nข้อมูลการใช้พลังงานและการปล่อยคาร์บอนสะสม (Carbon Footprint Summary):\n");
      if (carbonMap.isEmpty()) {
        sb.append("- ยังไม่มีข้อมูลการใช้พลังงานใดๆ บันทึกในระบบ\n");
      } else {
        for (var entry : carbonMap.entrySet()) {
          sb.append("- ").append(entry.getKey()).append(": ใช้ไปสะสมรวม ")
              .append(String.format("%.2f", entry.getValue()[0])).append(" ").append(unitMap.get(entry.getKey()))
              .append(" (คิดเป็นการปล่อยคาร์บอนสะสม ").append(String.format("%.2f", entry.getValue()[1])).append(" kgCO2e)\n");
        }
      }

      sb.append("\nความคืบหน้าแบบประเมินหลักเกณฑ์สำนักงานสีเขียว (Green Office Assessment Progress):\n");
      if (assessments.isEmpty()) {
        sb.append("- ยังไม่มีความคืบหน้าแบบประเมิน\n");
      } else {
        for (Assessment a : assessments) {
          sb.append("- การประเมินปี ").append(a.getAssessmentYear() != null ? a.getAssessmentYear() : 2026)
              .append(": สถานะ [").append(a.getStatus()).append("] (คะแนนรวม: ").append(a.getTotalScore()).append(")\n");
        }
      }
      sb.append("------------------------------------------------------\n");
      return sb.toString();
    } catch (Exception ctxErr) {
      log.error("[GeminiService] Failed to compile org context for AI prompt: {}", ctxErr.getMessage());
      return "";
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Object> validateEvidence(byte[] fileBytes, String mimeType, String categoryId) {
    String prompt =
        ("คุณคือผู้เชี่ยวชาญการตรวจประเมินสำนักงานสีเขียว (Green Office)\n"
                + "กรุณาวิเคราะห์เอกสารหลักฐานที่แนบมานี้ ว่ามีความสอดคล้องกับเกณฑ์การประเมินหมวดที่ %s หรือไม่\n"
                + "ให้ตอบกลับเป็น JSON format เท่านั้น ห้ามมีข้อความอื่น:\n"
                + "{\n"
                + "  \"isValid\": true/false,\n"
                + "  \"confidenceScore\": <ตัวเลข 0-100>,\n"
                + "  \"findings\": \"<สรุปสั้นๆ ว่าพบอะไรในเอกสารที่เกี่ยวข้องกับเกณฑ์>\",\n"
                + "  \"missingItems\": [\"<สิ่งที่ยังขาดหายไป หรือควรเพิ่มเติมเพื่อให้สมบูรณ์>\", ...]\n"
                + "}")
            .formatted(categoryId);
    try {
      String text = geminiClient.generateWithMedia(prompt, fileBytes, mimeType);
      return objectMapper.readValue(cleanJsonResponse(text), Map.class);
    } catch (Exception error) {
      log.error("Gemini Evidence Validation error: {}", error.getMessage());
      throw ApiException.badRequest(translateAiError(error));
    }
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> getChatHistory(Integer userId) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (ChatLog l : chatLogRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", l.getId());
      m.put("user_id", l.getUserId());
      m.put("question", l.getQuestion());
      m.put("answer", l.getAnswer());
      m.put("intent", l.getIntent());
      m.put("session_id", l.getSessionId());
      m.put("session_title", l.getSessionTitle());
      m.put("related_module", l.getRelatedModule());
      m.put("confidence_score", l.getConfidenceScore());
      m.put("created_at", l.getCreatedAt());
      result.add(m);
    }
    return result;
  }

  @Transactional
  public void clearChatHistory(Integer userId) {
    chatLogRepository.deleteByUserId(userId);
  }

  @Transactional
  public void deleteChatLogs(List<Integer> ids, Integer userId) {
    chatLogRepository.deleteByIdInAndUserId(ids, userId);
  }

  private String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public Map<String, Object> generateExecutiveSummary(Map<String, Object> data, Integer userId) {
    Organization org = resolveOrg(userId);
    Object greenScore = data.getOrDefault("greenScore", 0);
    Object carbonTotal = data.getOrDefault("carbonTotal", 0);
    Object orgTarget = data.getOrDefault("orgTarget", 0);
    Object extra = data.getOrDefault("extra", Map.of());
    String currentHash = sha256Hex(greenScore + "-" + carbonTotal + "-" + orgTarget + "-" + toJson(extra));

    if (org != null && isCooldownActive(org.getId()) && org.getCachedExecutiveSummary() != null) {
      log.info("[AI Cache] Circuit breaker active for Executive Summary. Serving cached summary.");
      Map<String, Object> res = new LinkedHashMap<>();
      res.put("summary", org.getCachedExecutiveSummary());
      res.put("lastAnalyzedAt", org.getLastSummaryAnalyzedAt());
      res.put("isFallback", true);
      return res;
    }

    if (org != null && currentHash.equals(org.getLastSummaryHash()) && org.getCachedExecutiveSummary() != null) {
      log.info("[AI Cache] Executive Summary cache hit for Org ID: {}", org.getId());
      Map<String, Object> res = new LinkedHashMap<>();
      res.put("summary", org.getCachedExecutiveSummary());
      res.put("lastAnalyzedAt", org.getLastSummaryAnalyzedAt());
      return res;
    }

    try {
      String prompt =
          ("คุณคือ AI ผู้เชี่ยวชาญด้าน Sustainability (ESG) ระดับองค์กร\n"
                  + "วิเคราะห์ข้อมูลต่อไปนี้เพื่อสรุป Executive Summary สั้นๆ แบบมืออาชีพ สำหรับผู้บริหาร:\n"
                  + "ข้อมูลองค์กร:\n"
                  + "- คะแนนสำนักงานสีเขียวปัจจุบัน: %s%%\n"
                  + "- เปรียบเทียบเป้าหมายการลดคาร์บอน: ปัจจุบัน %s tCO2e (เป้าหมายลด %s%%)\n"
                  + "- ข้อมูลเพิ่มเติม: %s\n\n"
                  + "ตอบกลับเป็นภาษาไทยเชิงธุรกิจ ความยาวไม่เกิน 4-5 ประโยค ชี้ให้เห็นถึงความเสี่ยง แนวโน้ม หรือความสำเร็จที่โดดเด่นเท่านั้น")
              .formatted(greenScore, carbonTotal, orgTarget, toJson(extra));

      log.info("[AI Cache] Executive Summary cache mismatch. Fetching fresh summary from Gemini...");
      String text = geminiClient.generateText(prompt);

      if (org != null) {
        org.setCachedExecutiveSummary(text);
        org.setLastSummaryHash(currentHash);
        org.setLastSummaryAnalyzedAt(Instant.now());
        organizationRepository.save(org);
      }
      return Map.of("summary", text);
    } catch (Exception error) {
      log.error("Gemini Executive Summary error: {}", error.getMessage());
      if (org != null) {
        String errMsg = String.valueOf(error.getMessage());
        String reason = (errMsg.contains("429") || errMsg.contains("quota")) ? "Rate limit (429) exceeded" : "API Connection Failure";
        setCooldown(org.getId(), 5, reason);
        if (org.getCachedExecutiveSummary() != null) {
          log.warn("[AI Cache] Gemini API failed. Falling back to previous cached summary.");
          Map<String, Object> res = new LinkedHashMap<>();
          res.put("summary", org.getCachedExecutiveSummary());
          res.put("lastAnalyzedAt", org.getLastSummaryAnalyzedAt());
          res.put("isFallback", true);
          return res;
        }
      }
      throw ApiException.badRequest(translateAiError(error));
    }
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public Map<String, Object> getRecommendations(Map<String, Object> data, Integer userId) {
    Organization org = resolveOrg(userId);
    Object weakPoints = data.getOrDefault("weakPoints", List.of());
    String currentHash = sha256Hex(toJson(weakPoints));

    if (org != null && isCooldownActive(org.getId()) && org.getCachedRecommendations() != null) {
      log.info("[AI Cache] Circuit breaker active for Recommendations. Serving cached recommendations.");
      try {
        Map<String, Object> parsed = objectMapper.readValue(org.getCachedRecommendations(), Map.class);
        parsed.put("isFallback", true);
        parsed.put("lastAnalyzedAt", org.getLastRecommendationsAnalyzedAt());
        return parsed;
      } catch (JsonProcessingException err) {
        log.error("[AI Cache] Failed to parse cached recommendations during cooldown.");
      }
    }

    if (org != null && currentHash.equals(org.getLastRecommendationsHash()) && org.getCachedRecommendations() != null) {
      log.info("[AI Cache] Recommendations cache hit for Org ID: {}", org.getId());
      try {
        return objectMapper.readValue(org.getCachedRecommendations(), Map.class);
      } catch (JsonProcessingException err) {
        log.error("[AI Cache] Failed to parse cached recommendations JSON, fetching fresh...");
      }
    }

    try {
      String prompt =
          ("คุณคือ AI Recommendation Engine ด้าน Green Office\n"
                  + "จากข้อมูลจุดอ่อนขององค์กรนี้: %s\n"
                  + "กรุณาสร้าง Action Plan เป็น JSON เท่านั้น ในรูปแบบ:\n"
                  + "{\n"
                  + "  \"recommendations\": [\n"
                  + "    {\n"
                  + "      \"title\": \"หัวข้อที่ควรปรับปรุง\",\n"
                  + "      \"action\": \"วิธีการปรับปรุงแบบรูปธรรม\",\n"
                  + "      \"expectedImpact\": \"High/Medium/Low\"\n"
                  + "    }\n"
                  + "  ],\n"
                  + "  \"missingDocuments\": [\"เอกสาร ก.\", \"เอกสาร ข.\"]\n"
                  + "}")
              .formatted(toJson(weakPoints));

      log.info("[AI Cache] Recommendations cache mismatch. Fetching fresh Action Plan from Gemini...");
      String text = cleanJsonResponse(geminiClient.generateText(prompt));
      Map<String, Object> parsed = objectMapper.readValue(text, Map.class);

      if (org != null) {
        org.setCachedRecommendations(text);
        org.setLastRecommendationsHash(currentHash);
        org.setLastRecommendationsAnalyzedAt(Instant.now());
        organizationRepository.save(org);
      }
      return parsed;
    } catch (Exception error) {
      log.error("Gemini Recommendations error: {}", error.getMessage());
      if (org != null) {
        String errMsg = String.valueOf(error.getMessage());
        String reason = (errMsg.contains("429") || errMsg.contains("quota")) ? "Rate limit (429) exceeded" : "API Connection Failure";
        setCooldown(org.getId(), 5, reason);
        if (org.getCachedRecommendations() != null) {
          log.warn("[AI Cache] Gemini API failed. Falling back to previous cached recommendations.");
          try {
            Map<String, Object> parsed = objectMapper.readValue(org.getCachedRecommendations(), Map.class);
            parsed.put("isFallback", true);
            parsed.put("lastAnalyzedAt", org.getLastRecommendationsAnalyzedAt());
            return parsed;
          } catch (JsonProcessingException err) {
            log.error("[AI Cache] Failed to parse fallback cached recommendations.");
          }
        }
      }
      throw ApiException.badRequest(translateAiError(error));
    }
  }

  private Organization resolveOrg(Integer userId) {
    if (userId == null) return null;
    User user = userRepository.findById(userId).orElse(null);
    return user != null ? user.getOrganization() : null;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }
}
