package th.ac.rmutt.greensync.uploads;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import th.ac.rmutt.greensync.common.ApiException;

/** Thin REST client for Supabase Storage, mirroring the NestJS UploadsService's use of the
 * Supabase JS SDK (there is no official Java SDK, so the storage HTTP API is called directly). */
@Service
public class SupabaseStorageService {

  private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);
  private static final long SIGNED_URL_TTL_SECONDS = 60L * 60 * 24 * 7;

  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final String baseUrl;
  private final String apiKey;
  private final String bucket;

  public SupabaseStorageService(
      @Value("${SUPABASE_URL:}") String supabaseUrl,
      @Value("${SUPABASE_KEY:}") String supabaseKey,
      @Value("${SUPABASE_BUCKET:}") String bucket) {
    this.baseUrl = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
    this.apiKey = supabaseKey;
    this.bucket = bucket;
    if (baseUrl.isBlank() || apiKey.isBlank() || bucket.isBlank()) {
      log.warn("Supabase configuration is missing (SUPABASE_URL/SUPABASE_KEY/SUPABASE_BUCKET)");
    } else {
      log.info("Supabase storage client initialized for bucket: {}", bucket);
    }
  }

  public boolean isConfigured() {
    return !baseUrl.isBlank() && !apiKey.isBlank() && !bucket.isBlank();
  }

  public String upload(String path, byte[] content, String contentType) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/storage/v1/object/" + bucket + "/" + path))
              .header("apikey", apiKey)
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", contentType != null ? contentType : "application/octet-stream")
              .header("x-upsert", "false")
              .POST(HttpRequest.BodyPublishers.ofByteArray(content))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        log.error("Supabase upload error ({}): {}", response.statusCode(), response.body());
        throw ApiException.badRequest("Supabase upload failed: " + response.body());
      }
      return signedOrPublicUrl(path);
    } catch (java.io.IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      log.error("Supabase upload threw {}: {}", e.getClass().getName(), e.getMessage(), e);
      throw ApiException.badRequest("Supabase upload failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private String signedOrPublicUrl(String path) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/storage/v1/object/sign/" + bucket + "/" + path))
              .header("apikey", apiKey)
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString("{\"expiresIn\":" + SIGNED_URL_TTL_SECONDS + "}"))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 300) {
        JsonNode node = objectMapper.readTree(response.body());
        String signedUrl = node.path("signedURL").asText(null);
        if (signedUrl != null && !signedUrl.isBlank()) {
          return baseUrl + "/storage/v1" + signedUrl;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to create signed URL for {}, falling back to public URL: {}", path, e.getMessage());
    }
    return baseUrl + "/storage/v1/object/public/" + bucket + "/" + path;
  }

  public void delete(String path) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/storage/v1/object/" + bucket))
              .header("apikey", apiKey)
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .method(
                  "DELETE",
                  HttpRequest.BodyPublishers.ofString("{\"prefixes\":[\"" + path + "\"]}"))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        log.error("Supabase delete error ({}): {}", response.statusCode(), response.body());
      }
    } catch (java.io.IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Supabase delete failed: {}", e.getMessage());
    }
  }

  /** Extracts the storage object path from a previously issued public or signed URL. */
  public String extractPath(String fileUrl) {
    if (fileUrl == null) return null;
    String publicMarker = "/object/public/" + bucket + "/";
    String signMarker = "/object/sign/" + bucket + "/";
    int idx = fileUrl.indexOf(publicMarker);
    if (idx >= 0) return fileUrl.substring(idx + publicMarker.length());
    idx = fileUrl.indexOf(signMarker);
    if (idx >= 0) {
      String rest = fileUrl.substring(idx + signMarker.length());
      int q = rest.indexOf('?');
      return q >= 0 ? rest.substring(0, q) : rest;
    }
    return null;
  }
}
