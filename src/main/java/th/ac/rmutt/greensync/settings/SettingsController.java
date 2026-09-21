package th.ac.rmutt.greensync.settings;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/settings")
public class SettingsController {

  private final SettingsService settingsService;

  public SettingsController(SettingsService settingsService) {
    this.settingsService = settingsService;
  }

  @GetMapping
  public Map<String, Object> getSettings() {
    return settingsService.getAllSettings();
  }

  @PutMapping
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public Map<String, Object> updateSettings(@RequestBody Map<String, Object> body) {
    return settingsService.updateSettings(body);
  }
}
