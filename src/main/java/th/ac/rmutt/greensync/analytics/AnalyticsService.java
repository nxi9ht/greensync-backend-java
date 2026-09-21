package th.ac.rmutt.greensync.analytics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.assessments.AssessmentRepository;
import th.ac.rmutt.greensync.assessments.EvidenceFileRepository;
import th.ac.rmutt.greensync.carbonlogs.CarbonLogRepository;
import th.ac.rmutt.greensync.organizations.OrganizationRepository;
import th.ac.rmutt.greensync.subscriptions.InvoiceRepository;
import th.ac.rmutt.greensync.subscriptions.OrganizationSubscriptionRepository;
import th.ac.rmutt.greensync.users.AssessorProfileRepository;
import th.ac.rmutt.greensync.users.UserRepository;

@Service
public class AnalyticsService {

  private final OrganizationRepository organizationRepository;
  private final UserRepository userRepository;
  private final InvoiceRepository invoiceRepository;
  private final AssessmentRepository assessmentRepository;
  private final OrganizationSubscriptionRepository organizationSubscriptionRepository;
  private final AssessorProfileRepository assessorProfileRepository;
  private final CarbonLogRepository carbonLogRepository;
  private final EvidenceFileRepository evidenceFileRepository;

  public AnalyticsService(
      OrganizationRepository organizationRepository,
      UserRepository userRepository,
      InvoiceRepository invoiceRepository,
      AssessmentRepository assessmentRepository,
      OrganizationSubscriptionRepository organizationSubscriptionRepository,
      AssessorProfileRepository assessorProfileRepository,
      CarbonLogRepository carbonLogRepository,
      EvidenceFileRepository evidenceFileRepository) {
    this.organizationRepository = organizationRepository;
    this.userRepository = userRepository;
    this.invoiceRepository = invoiceRepository;
    this.assessmentRepository = assessmentRepository;
    this.organizationSubscriptionRepository = organizationSubscriptionRepository;
    this.assessorProfileRepository = assessorProfileRepository;
    this.carbonLogRepository = carbonLogRepository;
    this.evidenceFileRepository = evidenceFileRepository;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getAdminStats() {
    long totalOrganizations = organizationRepository.count();
    long activeOrganizations = organizationRepository.countByActiveTrue();
    long totalUsers = userRepository.count();

    long verifiedAssessors = assessorProfileRepository.countByVerificationStatus("Verified");
    long pendingAssessors = assessorProfileRepository.countByVerificationStatus("Pending");
    long totalAssessors = verifiedAssessors + pendingAssessors;

    double subscriptionRevenue = invoiceRepository.sumAmountByStatus("PAID");
    Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
    double revenueMonth = invoiceRepository.sumAmountByStatusSince("PAID", thirtyDaysAgo);

    Map<String, Long> planCounts = new LinkedHashMap<>();
    for (var sub : organizationSubscriptionRepository.findAllWithPlan()) {
      String name = sub.getPlan() != null ? sub.getPlan().getPlanName() : "Free";
      planCounts.merge(name, 1L, Long::sum);
    }
    List<Map<String, Object>> planDistribution = new ArrayList<>();
    planCounts.forEach(
        (name, count) -> planDistribution.add(Map.of("name", name, "count", count)));

    long assessmentRequests = assessmentRepository.count();
    long approvedAssessments = assessmentRepository.countByStatus("APPROVED");
    long pendingAssessments = assessmentRepository.countByStatus("PENDING");
    long rejectedAssessments = assessmentRepository.countByStatus("REJECTED");

    double carbonReduction = carbonLogRepository.sumTotalEmission();

    long finishedAssessments = assessmentRepository.countByStatusIn(List.of("APPROVED", "REJECTED"));
    long successRate =
        finishedAssessments > 0 ? Math.round((approvedAssessments * 100.0) / finishedAssessments) : 0;

    long totalFiles = evidenceFileRepository.count();
    long totalSizeBytes = evidenceFileRepository.sumFileSize();
    double storageUsageGb = Math.round((totalSizeBytes / (1024.0 * 1024 * 1024)) * 10000) / 10000.0;

    Map<String, Object> assessmentStats = new LinkedHashMap<>();
    assessmentStats.put("total", assessmentRequests);
    assessmentStats.put("approved", approvedAssessments);
    assessmentStats.put("pending", pendingAssessments);
    assessmentStats.put("rejected", rejectedAssessments);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalOrganizations", totalOrganizations);
    result.put("activeOrganizations", activeOrganizations);
    result.put("totalUsers", totalUsers);
    result.put("assessmentRequests", assessmentRequests);
    result.put("carbonReduction", carbonReduction);
    result.put("assessorCount", totalAssessors);
    result.put("verifiedAssessors", verifiedAssessors);
    result.put("pendingAssessors", pendingAssessors);
    result.put("subscriptionRevenue", subscriptionRevenue);
    result.put("revenueMonth", revenueMonth);
    result.put("planDistribution", planDistribution);
    result.put("assessmentStats", assessmentStats);
    result.put("storageUsageGb", storageUsageGb);
    result.put("totalFiles", totalFiles);
    result.put("successRate", successRate);
    result.put("version", "2.0.1-sys-admin-java");
    return result;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getRevenueStats() {
    double totalRevenue = invoiceRepository.sumAmountByStatus("PAID");

    List<Map<String, Object>> trend = new ArrayList<>();
    for (Object[] row : invoiceRepository.monthlyRevenueTrend("PAID")) {
      Map<String, Object> point = new LinkedHashMap<>();
      point.put("month", row[0]);
      point.put("amount", ((Number) row[1]).doubleValue());
      trend.add(point);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("trend", trend);
    result.put("totalRevenue", totalRevenue);
    return result;
  }
}
