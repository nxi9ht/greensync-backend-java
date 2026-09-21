package th.ac.rmutt.greensync.gemini;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.gemini.dto.ChatRequest;
import th.ac.rmutt.greensync.gemini.dto.CreateSessionRequest;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

@RestController
@RequestMapping("/gemini")
public class GeminiController {

  private static final Set<String> ALLOWED_OCR_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf");

  private final GeminiService geminiService;

  public GeminiController(GeminiService geminiService) {
    this.geminiService = geminiService;
  }

  @PostMapping("/ocr")
  public Map<String, Object> uploadBill(@RequestParam("file") MultipartFile file) throws Exception {
    if (file == null || file.isEmpty()) {
      throw ApiException.badRequest("No file uploaded");
    }
    if (file.getContentType() == null || !ALLOWED_OCR_TYPES.contains(file.getContentType())) {
      throw ApiException.badRequest("Only image files (JPG, PNG, WEBP) and PDF are allowed");
    }
    return geminiService.ocr(file.getBytes(), file.getContentType());
  }

  @PostMapping("/chat")
  public Map<String, Object> chat(@RequestBody ChatRequest body, @AuthenticationPrincipal AuthenticatedUser me) {
    if (body.message == null || body.message.isBlank()) {
      throw ApiException.badRequest("Message is required");
    }
    return geminiService.chat(body.message, me.userId(), body.sessionId);
  }

  @GetMapping("/sessions")
  public List<Map<String, Object>> getSessions(@AuthenticationPrincipal AuthenticatedUser me) {
    return geminiService.getSessions(me.userId());
  }

  @PostMapping("/sessions")
  public Map<String, Object> createSession(
      @RequestBody(required = false) CreateSessionRequest body, @AuthenticationPrincipal AuthenticatedUser me) {
    String title = body != null && body.title != null && !body.title.isBlank() ? body.title : "New Conversation";
    return geminiService.createSession(me.userId(), title);
  }

  @GetMapping("/sessions/{id}/messages")
  public List<Map<String, Object>> getSessionMessages(
      @PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    return geminiService.getSessionMessages(id, me.userId());
  }

  @DeleteMapping("/sessions/{id}")
  public Map<String, Object> deleteSession(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    geminiService.deleteSession(id, me.userId());
    return Map.of("success", true);
  }

  @GetMapping("/history")
  public List<Map<String, Object>> getHistory(@AuthenticationPrincipal AuthenticatedUser me) {
    return geminiService.getChatHistory(me.userId());
  }

  @PostMapping("/evidence-validation")
  public Map<String, Object> validateEvidence(
      @RequestParam("file") MultipartFile file, @RequestParam(required = false) String categoryId) throws Exception {
    if (file == null || file.isEmpty()) {
      throw ApiException.badRequest("No file uploaded");
    }
    return geminiService.validateEvidence(
        file.getBytes(), file.getContentType(), categoryId != null ? categoryId : "ไม่ระบุหมวดหมู่");
  }

  @DeleteMapping("/history")
  public Map<String, Object> clearHistory(@AuthenticationPrincipal AuthenticatedUser me) {
    geminiService.clearChatHistory(me.userId());
    return Map.of("success", true);
  }

  @DeleteMapping("/history/{ids}")
  public Map<String, Object> deleteHistoryLogs(
      @PathVariable String ids, @AuthenticationPrincipal AuthenticatedUser me) {
    List<Integer> parsed =
        java.util.Arrays.stream(ids.split(","))
            .map(String::trim)
            .filter(s -> s.matches("\\d+"))
            .map(Integer::parseInt)
            .toList();
    if (!parsed.isEmpty()) {
      geminiService.deleteChatLogs(parsed, me.userId());
    }
    return Map.of("success", true);
  }

  @PostMapping("/executive-summary")
  public Map<String, Object> getExecutiveSummary(
      @RequestBody(required = false) Map<String, Object> body, @AuthenticationPrincipal AuthenticatedUser me) {
    return geminiService.generateExecutiveSummary(body != null ? body : Map.of(), me.userId());
  }

  @PostMapping("/recommendations")
  public Map<String, Object> getRecommendations(
      @RequestBody(required = false) Map<String, Object> body, @AuthenticationPrincipal AuthenticatedUser me) {
    return geminiService.getRecommendations(body != null ? body : Map.of(), me.userId());
  }
}
