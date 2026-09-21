package th.ac.rmutt.greensync.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * In-memory settings store mirroring the NestJS SettingsService: seeded from env defaults,
 * overlaid with whatever was last persisted to the local JSON file and the {@code
 * system_settings} DB table (DB wins last since it loads after the file, matching the NestJS
 * load order).
 */
@Service
public class SettingsService {

  private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
  private static final Set<String> MASKED_KEYS = Set.of("stripe.secret_key", "stripe.webhook_secret", "smtp.pass");

  private final Map<String, String> settingsMap = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JdbcTemplate jdbcTemplate;
  private final Path storageFilePath;

  @Value("${DEFAULT_BASE_YEAR:2024}")
  private String defaultBaseYear;

  @Value("${SYSTEM_NAME:GREEN SYNC}")
  private String systemName;

  @Value("${MAINTENANCE_MODE:false}")
  private String maintenanceMode;

  @Value("${CARBON_STANDARD:TGO}")
  private String carbonStandard;

  @Value("${CARBON_THRESHOLD:50000}")
  private String carbonThreshold;

  @Value("${STRIPE_PUBLIC_KEY:}")
  private String stripePublicKey;

  @Value("${STRIPE_SECRET_KEY:}")
  private String stripeSecretKey;

  @Value("${STRIPE_WEBHOOK_SECRET:}")
  private String stripeWebhookSecret;

  @Value("${INDUSTRY_BENCHMARK:12000}")
  private String industryBenchmark;

  @Value("${SMTP_MODE:mock}")
  private String smtpMode;

  @Value("${SMTP_HOST:}")
  private String smtpHost;

  @Value("${SMTP_PORT:587}")
  private String smtpPort;

  @Value("${SMTP_USER:}")
  private String smtpUser;

  @Value("${SMTP_PASS:}")
  private String smtpPass;

  public SettingsService(JdbcTemplate jdbcTemplate, @Value("${SETTINGS_FILE_PATH:data/admin_settings.json}") String storageFilePath) {
    this.jdbcTemplate = jdbcTemplate;
    this.storageFilePath = Path.of(storageFilePath);
  }

  @PostConstruct
  void init() {
    seedDefaults();
    loadFromFile();
    loadFromDatabase();
  }

  private void seedDefaults() {
    settingsMap.put("defaultBaseYear", defaultBaseYear);
    settingsMap.put("systemName", systemName);
    settingsMap.put("maintenanceMode", maintenanceMode);
    settingsMap.put("carbonStandard", carbonStandard);
    settingsMap.put("carbonThreshold", carbonThreshold);
    settingsMap.put("permission.manage_quota", "System Admin");
    settingsMap.put("permission.ai_scan", "System Admin");
    settingsMap.put("permission.green_office", "System Admin");
    settingsMap.put("stripe.public_key", stripePublicKey);
    settingsMap.put("stripe.secret_key", stripeSecretKey);
    settingsMap.put("stripe.webhook_secret", stripeWebhookSecret);
    settingsMap.put("stripe.currency", "thb");
    settingsMap.put("industry_benchmark", industryBenchmark);
    settingsMap.put("smtp.mode", smtpMode);
    settingsMap.put("smtp.host", smtpHost);
    settingsMap.put("smtp.port", smtpPort);
    settingsMap.put("smtp.user", smtpUser);
    settingsMap.put("smtp.pass", smtpPass);
    settingsMap.put("smtp.sender", "Green Office System <no-reply@greensync.com>");
    settingsMap.put("smtp.fallback_email", "admin@greensync.com");
  }

  @SuppressWarnings("unchecked")
  private void loadFromFile() {
    try {
      if (Files.exists(storageFilePath)) {
        Map<String, Object> parsed = objectMapper.readValue(Files.readString(storageFilePath), Map.class);
        parsed.forEach((key, value) -> {
          if (value != null) settingsMap.put(key, String.valueOf(value));
        });
        log.info("Loaded {} persistent settings from {}", parsed.size(), storageFilePath);
      }
    } catch (IOException e) {
      log.error("Failed to load persistent settings from {}: {}", storageFilePath, e.getMessage());
    }
  }

  private void loadFromDatabase() {
    try {
      var rows = jdbcTemplate.queryForList("SELECT key, value FROM system_settings");
      for (var row : rows) {
        Object key = row.get("key");
        Object value = row.get("value");
        if (key != null && value != null) {
          settingsMap.put(String.valueOf(key), String.valueOf(value));
        }
      }
      if (!rows.isEmpty()) {
        log.info("Loaded {} settings from database table system_settings", rows.size());
      }
    } catch (Exception e) {
      log.warn("Database settings storage initialization skipped/failed: {}", e.getMessage());
    }
  }

  private void saveToDatabase(String key, String value) {
    try {
      jdbcTemplate.update(
          "INSERT INTO system_settings (key, value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
              + "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = CURRENT_TIMESTAMP",
          key,
          value);
    } catch (Exception e) {
      log.warn("Failed to persist setting '{}' to database: {}", key, e.getMessage());
    }
  }

  private void saveToFile() {
    try {
      Path dir = storageFilePath.toAbsolutePath().getParent();
      if (dir != null && !Files.exists(dir)) {
        Files.createDirectories(dir);
      }
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFilePath.toFile(), settingsMap);
      log.info("Persisted settings to {}", storageFilePath);
    } catch (IOException e) {
      log.error("Failed to persist settings to {}: {}", storageFilePath, e.getMessage());
    }
  }

  public Map<String, Object> getAllSettings() {
    Map<String, Object> result = new LinkedHashMap<>();
    settingsMap.forEach((key, value) -> {
      if (MASKED_KEYS.contains(key)) {
        result.put(key, (value == null || value.isBlank()) ? "" : "••••••••");
      } else {
        result.put(key, parseValue(key, value));
      }
    });
    return result;
  }

  public Object getSetting(String key) {
    String value = settingsMap.get(key);
    return value == null ? null : parseValue(key, value);
  }

  private Object parseValue(String key, String value) {
    if ("true".equals(value)) return true;
    if ("false".equals(value)) return false;
    if (!key.startsWith("payment.") && !key.startsWith("stripe.") && !key.startsWith("smtp.") && !value.isEmpty()) {
      try {
        return Double.parseDouble(value);
      } catch (NumberFormatException ignored) {
        // fall through: not numeric
      }
    }
    return value;
  }

  public Map<String, Object> updateSettings(Map<String, Object> settings) {
    settings.forEach((key, value) -> {
      String valStr = String.valueOf(value);
      if (MASKED_KEYS.contains(key) && (valStr.contains("•") || valStr.isEmpty())) {
        if (settingsMap.containsKey(key)) return;
      }
      settingsMap.put(key, valStr);
      saveToDatabase(key, valStr);
    });
    saveToFile();
    return getAllSettings();
  }
}
