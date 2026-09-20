package th.ac.rmutt.greensync.carbonlogs;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import th.ac.rmutt.greensync.carbonlogs.dto.EmissionFactorRequest;

@RestController
@RequestMapping("/admin/emission-factors")
public class EmissionFactorsController {

  private final EmissionFactorsService emissionFactorsService;

  public EmissionFactorsController(EmissionFactorsService emissionFactorsService) {
    this.emissionFactorsService = emissionFactorsService;
  }

  @GetMapping
  public List<Map<String, Object>> findAll() {
    return emissionFactorsService.findAll();
  }

  @PostMapping
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public Map<String, Object> create(@RequestBody EmissionFactorRequest request) {
    return emissionFactorsService.create(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public Map<String, Object> update(@PathVariable Integer id, @RequestBody EmissionFactorRequest request) {
    return emissionFactorsService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public void remove(@PathVariable Integer id) {
    emissionFactorsService.remove(id);
  }
}
