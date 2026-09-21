package th.ac.rmutt.greensync.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Thin REST client for the Gemini "generateContent" endpoint, mirroring the NestJS
 * GeminiService's use of the {@code @google/genai} SDK (there is no comparable first-class
 * Java SDK dependency already in this project, so the public REST API is called directly). */
@Component
public class GeminiClient {

  private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
  private static final String MODEL = "gemini-2.5-flash";
  private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
  private static final Duration TIMEOUT = Duration.ofSeconds(15);

  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final String apiKey;
  private final String backupApiKey;

  public GeminiClient(
      @Value("${app.gemini.api-key:}") String apiKey, @Value("${app.gemini.backup-api-key:}") String backupApiKey) {
    this.apiKey = apiKey;
    this.backupApiKey = backupApiKey;
  }

  public boolean isConfigured() {
    return !apiKey.isBlank() && !apiKey.equals("your_api_key_here") && !apiKey.equals("your_secure_api_key_here");
  }

  /** Sends a plain-text prompt, falling back to the backup key if the primary key fails. */
  public String generateText(String prompt) {
    ObjectNode body = objectMapper.createObjectNode();
    ArrayNode contents = body.putArray("contents");
    ObjectNode content = contents.addObject();
    ArrayNode parts = content.putArray("parts");
    parts.addObject().put("text", prompt);
    return executeWithFallback(body);
  }

  /** Sends a text prompt plus an inline image/PDF, falling back to the backup key on failure. */
  public String generateWithMedia(String prompt, byte[] fileBytes, String mimeType) {
    ObjectNode body = objectMapper.createObjectNode();
    ArrayNode contents = body.putArray("contents");
    ObjectNode content = contents.addObject();
    content.put("role", "user");
    ArrayNode parts = content.putArray("parts");
    parts.addObject().put("text", prompt);
    ObjectNode inlineData = parts.addObject().putObject("inlineData");
    inlineData.put("mimeType", mimeType);
    inlineData.put("data", Base64.getEncoder().encodeToString(fileBytes));
    return executeWithFallback(body);
  }

  private String executeWithFallback(ObjectNode body) {
    if (!isConfigured()) {
      throw new GeminiException("GEMINI_API_KEY is not configured. Please set it in .env and restart the server.");
    }
    try {
      return callGemini(body, apiKey);
    } catch (GeminiTimeoutException timeout) {
      throw timeout;
    } catch (Exception primaryError) {
      log.warn("[GeminiClient] Primary API key failed, checking backup key... {}", primaryError.getMessage());
      if (backupApiKey != null
          && !backupApiKey.isBlank()
          && !backupApiKey.equals("your_backup_api_key_here")
          && !backupApiKey.equals("your_backup_gemini_api_key_here")) {
        try {
          return callGemini(body, backupApiKey);
        } catch (Exception backupError) {
          log.error("[GeminiClient] Both primary and backup API keys failed! {}", backupError.getMessage());
          if (backupError instanceof GeminiTimeoutException timeoutErr) {
            throw timeoutErr;
          }
          throw wrap(primaryError);
        }
      }
      throw wrap(primaryError);
    }
  }

  private RuntimeException wrap(Exception e) {
    return e instanceof RuntimeException re ? re : new GeminiException(e.getMessage());
  }

  private String callGemini(ObjectNode body, String key) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(API_BASE + MODEL + ":generateContent?key=" + key))
              .timeout(TIMEOUT)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 429) {
        throw new GeminiException("429 quota exceeded");
      }
      if (response.statusCode() == 401 || response.statusCode() == 403) {
        throw new GeminiException("api_key unauthorized");
      }
      if (response.statusCode() >= 300) {
        throw new GeminiException("Gemini API error " + response.statusCode() + ": " + response.body());
      }
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
      return textNode.isMissingNode() ? "" : textNode.asText("").trim();
    } catch (java.net.http.HttpTimeoutException e) {
      throw new GeminiTimeoutException("Timeout");
    } catch (java.io.IOException e) {
      throw new GeminiException(e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GeminiException(e.getMessage());
    }
  }

  public static class GeminiException extends RuntimeException {
    public GeminiException(String message) {
      super(message);
    }
  }

  public static class GeminiTimeoutException extends GeminiException {
    public GeminiTimeoutException(String message) {
      super(message);
    }
  }
}
