package th.ac.rmutt.greensync.carbonlogs;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.carbonlogs.dto.EmissionFactorRequest;
import th.ac.rmutt.greensync.common.ApiException;

@Service
public class EmissionFactorsService {

  private static final Logger log = LoggerFactory.getLogger(EmissionFactorsService.class);
  private static final long CACHE_TTL_MS = 60_000;

  private final EmissionFactorRepository repository;
  private final CarbonLogMapper mapper;

  private volatile List<EmissionFactor> cached;
  private volatile long lastFetchTime;

  public EmissionFactorsService(EmissionFactorRepository repository, CarbonLogMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAll() {
    return factors().stream().map(mapper::toFactorMap).toList();
  }

  private List<EmissionFactor> factors() {
    long now = System.currentTimeMillis();
    if (cached != null && now - lastFetchTime < CACHE_TTL_MS) {
      return cached;
    }
    cached = repository.findAllByOrderByNameAsc();
    lastFetchTime = now;
    return cached;
  }

  @Transactional
  public Map<String, Object> create(EmissionFactorRequest req) {
    cached = null;
    EmissionFactor entity = new EmissionFactor();
    applyFields(entity, req);
    EmissionFactor saved = repository.save(entity);
    log.info("Added Emission Factor: {}", saved.getName());
    return mapper.toFactorMap(saved);
  }

  @Transactional
  public Map<String, Object> update(Integer id, EmissionFactorRequest req) {
    cached = null;
    EmissionFactor existing = repository.findById(id).orElseThrow(() -> ApiException.notFound("ไม่พบค่าสัมประสิทธิ์ที่ระบุ"));
    applyFields(existing, req);
    EmissionFactor saved = repository.save(existing);
    log.info("Updated Emission Factor: {}", saved.getName());
    return mapper.toFactorMap(saved);
  }

  @Transactional
  public void remove(Integer id) {
    cached = null;
    repository.findById(id).ifPresent(item -> log.info("Deleted Emission Factor: {}", item.getName()));
    repository.deleteById(id);
  }

  private void applyFields(EmissionFactor entity, EmissionFactorRequest req) {
    if (req.name != null) entity.setName(req.name);
    if (req.scope != null) entity.setScope(req.scope);
    if (req.unit != null) entity.setUnit(req.unit);
    if (req.factor_value != null) entity.setFactorValue(req.factor_value);
    if (req.year != null) entity.setYear(req.year);
    if (req.source != null) entity.setSource(req.source);
  }
}
