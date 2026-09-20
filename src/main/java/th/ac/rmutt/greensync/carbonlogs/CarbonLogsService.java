package th.ac.rmutt.greensync.carbonlogs;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.carbonlogs.dto.CarbonLogRequest;
import th.ac.rmutt.greensync.organizations.Organization;
import th.ac.rmutt.greensync.users.UserProfile;
import th.ac.rmutt.greensync.users.UserProfileRepository;

@Service
public class CarbonLogsService {

  private final CarbonLogRepository logRepository;
  private final EmissionFactorRepository emissionFactorRepository;
  private final CarbonLogMapper mapper;
  private final UserProfileRepository userProfileRepository;

  public CarbonLogsService(
      CarbonLogRepository logRepository,
      EmissionFactorRepository emissionFactorRepository,
      CarbonLogMapper mapper,
      UserProfileRepository userProfileRepository) {
    this.logRepository = logRepository;
    this.emissionFactorRepository = emissionFactorRepository;
    this.mapper = mapper;
    this.userProfileRepository = userProfileRepository;
  }

  @Transactional
  public Map<String, Object> create(CarbonLogRequest req, Integer orgId) {
    CarbonLog log = new CarbonLog();
    Organization org = new Organization();
    org.setId(orgId);
    log.setOrganization(org);
    applyFields(log, req);

    if (req.emission_factor_id != null && req.usage_amount != null) {
      emissionFactorRepository
          .findById(req.emission_factor_id)
          .ifPresent(
              factor -> {
                if (factor.getFactorValue() != null) {
                  log.setTotalEmission(calculateEmission(req.usage_amount, factor.getFactorValue()));
                }
              });
    }

    logRepository.save(log);
    return mapper.toMap(logRepository.findById(log.getId()).orElseThrow());
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAll(Integer orgId, Integer page, Integer limit) {
    int safeLimit = limit != null ? Math.min(Math.max(1, limit), 200) : 50;
    int safePage = page != null ? Math.max(1, page) : 1;
    return logRepository
        .findByOrganizationIdOrderByYearDescMonthDescCreatedAtDesc(orgId, PageRequest.of(safePage - 1, safeLimit))
        .stream()
        .map(mapper::toMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> update(Integer id, Integer orgId, CarbonLogRequest req) {
    CarbonLog log =
        logRepository.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> ApiException.notFound("ไม่พบข้อมูลรายการนี้"));
    applyFields(log, req);

    Integer efId = req.emission_factor_id != null ? req.emission_factor_id : (log.getEmissionFactor() != null ? log.getEmissionFactor().getId() : null);
    Double usage = req.usage_amount != null ? req.usage_amount : log.getUsageAmount();
    if (efId != null && usage != null) {
      emissionFactorRepository
          .findById(efId)
          .ifPresent(
              factor -> {
                if (factor.getFactorValue() != null) {
                  log.setTotalEmission(calculateEmission(usage, factor.getFactorValue()));
                }
              });
    }

    logRepository.save(log);
    return mapper.toMap(logRepository.findById(id).orElseThrow());
  }

  @Transactional
  public void remove(Integer id, Integer orgId) {
    CarbonLog log =
        logRepository.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> ApiException.notFound("ไม่พบข้อมูลรายการนี้"));
    logRepository.delete(log);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> getCarbonTrend(Integer orgId, Instant startDate, Instant endDate) {
    List<CarbonLog> logs =
        logRepository.findByOrganizationIdOrderByCreatedAtAsc(orgId).stream()
            .filter(l -> startDate == null || (l.getCreatedAt() != null && !l.getCreatedAt().isBefore(startDate)))
            .filter(l -> endDate == null || (l.getCreatedAt() != null && !l.getCreatedAt().isAfter(endDate)))
            .toList();
    return monthlyTrend(logs);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getPersonalDashboard(Integer userId, Integer orgId) {
    if (orgId == null) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("userId", userId);
      result.put("totalEmission", 0);
      result.put("trend", List.of());
      result.put("logCount", 0);
      result.put("note", "ไม่พบข้อมูลองค์กรที่สังกัด");
      return result;
    }

    List<CarbonLog> logs = logRepository.findByOrganizationIdOrderByCreatedAtAsc(orgId);
    List<Map<String, Object>> trend = monthlyTrend(logs);
    double totalEmission = logs.stream().mapToDouble(l -> l.getTotalEmission() != null ? l.getTotalEmission() : 0).sum();

    double personalGoalPercent =
        userProfileRepository
            .findByUserId(userId)
            .map(UserProfile::getPersonalGoalPercent)
            .map(java.math.BigDecimal::doubleValue)
            .orElse(0.0);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("userId", userId);
    result.put("orgId", orgId);
    result.put("totalEmission", Math.round(totalEmission * 100.0) / 100.0);
    result.put("trend", trend);
    result.put("logCount", logs.size());
    result.put("personalGoalPercent", personalGoalPercent);
    result.put("note", "ข้อมูลรวมขององค์กรที่สังกัด");
    return result;
  }

  private List<Map<String, Object>> monthlyTrend(List<CarbonLog> logs) {
    Map<String, Double> byMonth = new TreeMap<>();
    for (CarbonLog l : logs) {
      String monthKey = monthKeyOf(l);
      byMonth.merge(monthKey, l.getTotalEmission() != null ? l.getTotalEmission() : 0, Double::sum);
    }
    return byMonth.entrySet().stream()
        .map(e -> Map.<String, Object>of("month", e.getKey(), "emission", e.getValue()))
        .toList();
  }

  private String monthKeyOf(CarbonLog l) {
    int year;
    int month;
    if (l.getCreatedAt() != null) {
      var dt = l.getCreatedAt().atZone(ZoneOffset.UTC);
      year = dt.get(ChronoField.YEAR);
      month = dt.get(ChronoField.MONTH_OF_YEAR);
    } else {
      year = l.getYear() != null ? l.getYear() : Instant.now().atZone(ZoneOffset.UTC).getYear();
      month = l.getMonth() != null ? l.getMonth() : 1;
    }
    return "%d-%02d".formatted(year, month);
  }

  private void applyFields(CarbonLog log, CarbonLogRequest req) {
    if (req.activity_type != null) log.setActivityType(req.activity_type);
    if (req.month != null) log.setMonth(req.month);
    if (req.year != null) log.setYear(req.year);
    if (req.usage_amount != null) log.setUsageAmount(req.usage_amount);
    if (req.total_emission != null) log.setTotalEmission(req.total_emission);
    if (req.evidence_url != null) log.setEvidenceUrl(req.evidence_url);
    if (req.data_source != null) log.setDataSource(req.data_source);
    if (req.emission_factor_id != null) {
      emissionFactorRepository.findById(req.emission_factor_id).ifPresent(log::setEmissionFactor);
    }
  }

  /** Mirrors the NestJS calculateEmission() util exactly. */
  private static double calculateEmission(double usageAmount, double factorValue) {
    if (Double.isNaN(usageAmount) || Double.isNaN(factorValue) || usageAmount < 0 || factorValue < 0) {
      return 0;
    }
    return Math.round(usageAmount * factorValue * 10000.0) / 10000.0;
  }
}
