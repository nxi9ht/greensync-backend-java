package th.ac.rmutt.greensync.executive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.assessments.Assessment;
import th.ac.rmutt.greensync.assessments.AssessmentRepository;
import th.ac.rmutt.greensync.carbonlogs.CarbonLog;
import th.ac.rmutt.greensync.carbonlogs.CarbonLogRepository;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.organizations.Organization;
import th.ac.rmutt.greensync.organizations.OrganizationRepository;

@Service
public class ExecutiveService {

  /**
   * The NestJS service filters carbon logs by "log.date", a column that doesn't exist on the
   * carbon_activity_logs table (it has year/month/created_at, not date) — every startDate/endDate
   * filter there would throw if it ever ran. Since the frontend never actually sends those
   * params to this endpoint, date filtering is simply not implemented here; only branchId (a
   * real column, org_unit_id) is honored.
   */
  private static final double DEFAULT_INDUSTRY_BENCHMARK = 12000;

  private final AssessmentRepository assessmentRepository;
  private final CarbonLogRepository carbonLogRepository;
  private final OrganizationRepository organizationRepository;

  public ExecutiveService(
      AssessmentRepository assessmentRepository,
      CarbonLogRepository carbonLogRepository,
      OrganizationRepository organizationRepository) {
    this.assessmentRepository = assessmentRepository;
    this.carbonLogRepository = carbonLogRepository;
    this.organizationRepository = organizationRepository;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getDashboard(Integer orgId, Integer branchId) {
    Organization org = organizationRepository.findById(orgId).orElseThrow(() -> ApiException.notFound("ไม่พบข้อมูลองค์กร"));

    List<Assessment> approvedAssessments =
        assessmentRepository.findByOrganizationIdAndStatusOrderByUpdatedAtDesc(orgId, "APPROVED");
    List<Map<String, Object>> carbonByScope = getCarbonByScope(orgId, branchId);
    List<Map<String, Object>> carbonByUnit = getCarbonByUnit(orgId, branchId);

    int approvedCount = approvedAssessments.size();
    double avgApprovedScore =
        approvedCount > 0
            ? Math.round(
                    approvedAssessments.stream().mapToDouble(a -> a.getTotalScore() != null ? a.getTotalScore() : 0).sum()
                        / approvedCount
                        * 100)
                / 100.0
            : 0;

    String latestCertifiedLevel =
        approvedAssessments.stream()
            .filter(a -> a.getCertifiedLevel() != null)
            .findFirst()
            .map(Assessment::getCertifiedLevel)
            .orElse(null);

    double targetReductionPercent = org.getTargetReductionPercent() != null ? org.getTargetReductionPercent() : 0;
    int baseYear = org.getBaseYear() != null ? org.getBaseYear() : java.time.Year.now().getValue();
    double netZeroProgressPercent = calculateNetZeroProgressPercent(baseYear, targetReductionPercent, carbonByScope);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("orgId", org.getId());
    result.put("orgName", org.getName());
    result.put("targetReductionPercent", targetReductionPercent);
    result.put("approvedCount", approvedCount);
    result.put("avgApprovedScore", avgApprovedScore);
    result.put("latestCertifiedLevel", latestCertifiedLevel);
    result.put("netZeroProgressPercent", netZeroProgressPercent);
    result.put("approvedAssessments", approvedAssessments.stream().map(this::toApprovedAssessment).toList());
    result.put("carbonByScope", carbonByScope);
    result.put("carbonByUnit", carbonByUnit);
    return result;
  }

  private Map<String, Object> toApprovedAssessment(Assessment a) {
    var cert = a.getCertificates().stream().findFirst().orElse(null);
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", a.getId());
    map.put("assessmentYear", a.getAssessmentYear());
    map.put("totalScore", a.getTotalScore() != null ? a.getTotalScore() : 0);
    map.put("certifiedLevel", a.getCertifiedLevel());
    map.put("approvedAt", a.getUpdatedAt());
    map.put("certificateUrl", cert != null ? cert.getCertificateUrl() : null);
    map.put("certificateNo", cert != null ? cert.getCertificateNo() : null);
    map.put("issuedAt", cert != null ? cert.getIssuedAt() : null);
    map.put("expiredAt", cert != null ? cert.getExpiredAt() : null);
    return map;
  }

  private List<CarbonLog> logsForOrg(Integer orgId, Integer branchId) {
    List<CarbonLog> logs = carbonLogRepository.findByOrganizationIdOrderByCreatedAtAsc(orgId);
    if (branchId != null) {
      logs =
          logs.stream()
              .filter(l -> l.getOrganizationUnit() != null && branchId.equals(l.getOrganizationUnit().getId()))
              .toList();
    }
    return logs;
  }

  private List<Map<String, Object>> getCarbonByScope(Integer orgId, Integer branchId) {
    Map<String, Double> scopeSums = new LinkedHashMap<>();
    for (CarbonLog log : logsForOrg(orgId, branchId)) {
      int scope = inferScope(log);
      int year = log.getYear() != null ? log.getYear() : java.time.Year.now().getValue();
      String key = scope + "-" + year;
      scopeSums.merge(key, log.getTotalEmission() != null ? log.getTotalEmission() : 0, Double::sum);
    }

    List<Map<String, Object>> points = new ArrayList<>();
    scopeSums.forEach(
        (key, total) -> {
          String[] parts = key.split("-");
          Map<String, Object> point = new LinkedHashMap<>();
          point.put("scope", Integer.parseInt(parts[0]));
          point.put("year", Integer.parseInt(parts[1]));
          point.put("totalEmission", total);
          points.add(point);
        });
    points.sort(
        Comparator.<Map<String, Object>>comparingInt(p -> (int) p.get("year"))
            .thenComparingInt(p -> (int) p.get("scope")));
    return points;
  }

  private int inferScope(CarbonLog log) {
    if (log.getEmissionFactor() != null && log.getEmissionFactor().getScope() != null) {
      return log.getEmissionFactor().getScope();
    }
    String type = log.getActivityType() != null ? log.getActivityType().toLowerCase() : "";
    if (type.contains("electricity") || type.contains("ไฟ")) return 2;
    if (type.contains("water") || type.contains("น้ำ") || type.contains("ขยะ") || type.contains("กระดาษ")) return 3;
    return 1;
  }

  private List<Map<String, Object>> getCarbonByUnit(Integer orgId, Integer branchId) {
    List<CarbonLog> logs = logsForOrg(orgId, branchId);

    Map<String, Double> byUnitName = new TreeMap<>();
    for (CarbonLog log : logs) {
      String unitName = log.getOrganizationUnit() != null ? log.getOrganizationUnit().getUnitName() : null;
      byUnitName.merge(
          unitName == null ? "" : unitName, log.getTotalEmission() != null ? log.getTotalEmission() : 0, Double::sum);
    }

    // Percentile is computed across all branches of the org (unfiltered by branchId), matching
    // the NestJS service querying org-wide branch totals separately from the display rows.
    // groupingBy() throws on a null key, and most logs have no org_unit_id set, so logs
    // without a unit are grouped under a sentinel (0) instead of the unit's real id.
    List<Double> allBranchTotals =
        carbonLogRepository.findByOrganizationIdOrderByCreatedAtAsc(orgId).stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    l -> l.getOrganizationUnit() != null ? l.getOrganizationUnit().getId() : 0,
                    java.util.stream.Collectors.summingDouble(
                        l -> l.getTotalEmission() != null ? l.getTotalEmission() : 0)))
            .values()
            .stream()
            .sorted()
            .toList();

    double industryAverage = DEFAULT_INDUSTRY_BENCHMARK; // settings module not ported yet

    return byUnitName.entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .map(
            entry -> {
              double emission = entry.getValue();
              int percentile = 100;
              if (allBranchTotals.size() > 1) {
                long countHigher = allBranchTotals.stream().filter(v -> v > emission).count();
                percentile = Math.max(1, Math.round((float) countHigher / (allBranchTotals.size() - 1) * 100));
              }
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("unitName", entry.getKey().isEmpty() ? "หน่วยงานกลาง" : entry.getKey());
              row.put("totalEmission", emission);
              row.put("industryAverage", industryAverage);
              row.put("percentile", percentile);
              return row;
            })
        .toList();
  }

  @Transactional
  public Map<String, Object> setGoal(Integer orgId, Double targetReductionPercent, Integer year) {
    Organization org = organizationRepository.findById(orgId).orElseThrow(() -> ApiException.notFound("Organization not found"));
    org.setTargetReductionPercent(targetReductionPercent);
    org.setTargetYear(year);
    organizationRepository.save(org);
    return Map.of("success", true, "targetReductionPercent", targetReductionPercent, "year", year);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> getLeaderboard(Integer orgId, Integer year) {
    int targetYear = year != null ? year : java.time.Year.now().getValue();
    int prevYear = targetYear - 1;

    List<CarbonLog> allLogs = carbonLogRepository.findByOrganizationIdOrderByCreatedAtAsc(orgId);

    Map<String, Double> currentByUnit = sumByUnitForYear(allLogs, targetYear);
    Map<String, Double> prevByUnit = sumByUnitForYear(allLogs, prevYear);

    record Item(String unitName, double totalEmission, double reductionPercent) {}

    List<Item> items =
        currentByUnit.entrySet().stream()
            .map(
                e -> {
                  double current = e.getValue();
                  double prev = prevByUnit.getOrDefault(e.getKey(), 0.0);
                  double reductionPercent = prev > 0 ? Math.round((prev - current) / prev * 100 * 100) / 100.0 : 0;
                  return new Item(e.getKey(), current, reductionPercent);
                })
            .sorted(
                Comparator.comparingDouble(Item::reductionPercent)
                    .reversed()
                    .thenComparingDouble(Item::totalEmission))
            .toList();

    List<Assessment> approvedAssessments = assessmentRepository.findByOrganizationIdAndStatusOrderByUpdatedAtDesc(orgId, "APPROVED");
    double avgScore =
        approvedAssessments.isEmpty()
            ? 0
            : Math.round(
                    approvedAssessments.stream().mapToDouble(a -> a.getTotalScore() != null ? a.getTotalScore() : 0).sum()
                        / approvedAssessments.size()
                        * 100)
                / 100.0;

    List<Map<String, Object>> result = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      Item item = items.get(i);
      int rank = i + 1;
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("rank", rank);
      row.put("unitName", item.unitName());
      row.put("totalEmission", item.totalEmission());
      row.put("reductionPercent", item.reductionPercent());
      row.put("badge", badgeFor(rank, item.reductionPercent()));
      row.put("assessmentScore", avgScore);
      result.add(row);
    }
    return result;
  }

  private Map<String, Double> sumByUnitForYear(List<CarbonLog> logs, int year) {
    Map<String, Double> byUnit = new LinkedHashMap<>();
    for (CarbonLog log : logs) {
      if (log.getYear() == null || log.getYear() != year) continue;
      String unitName = log.getOrganizationUnit() != null ? log.getOrganizationUnit().getUnitName() : "หน่วยงานกลาง";
      byUnit.merge(unitName, log.getTotalEmission() != null ? log.getTotalEmission() : 0, Double::sum);
    }
    return byUnit;
  }

  private String badgeFor(int rank, double reductionPercent) {
    if (rank == 1 || reductionPercent >= 20) return "🥇 ทองคำ";
    if (rank <= 3 || reductionPercent >= 10) return "🥈 เงิน";
    if (rank <= 5 || reductionPercent >= 5) return "🥉 ทองแดง";
    return "🌱 มุ่งมั่น";
  }

  private double calculateNetZeroProgressPercent(int baseYear, double targetReductionPercent, List<Map<String, Object>> scopePoints) {
    if (scopePoints.isEmpty() || targetReductionPercent <= 0) {
      return 0;
    }
    Map<Integer, Double> byYear = new TreeMap<>();
    for (Map<String, Object> point : scopePoints) {
      int year = (int) point.get("year");
      double emission = (double) point.get("totalEmission");
      byYear.merge(year, emission, Double::sum);
    }

    Double baseline = byYear.get(baseYear);
    int latestYear = byYear.keySet().stream().mapToInt(Integer::intValue).max().orElse(baseYear);
    Double latest = byYear.get(latestYear);
    if (baseline == null || latest == null || baseline <= 0) {
      return 0;
    }

    double actualReductionPercent = (baseline - latest) / baseline * 100;
    double progress = actualReductionPercent / targetReductionPercent * 100;
    return Math.round(Math.max(0, Math.min(100, progress)) * 100) / 100.0;
  }
}
