package th.ac.rmutt.greensync.organizations;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.ac.rmutt.greensync.common.ApiException;
import th.ac.rmutt.greensync.organizations.dto.OrganizationRequest;
import th.ac.rmutt.greensync.organizations.dto.OrganizationUnitRequest;

@Service
public class OrganizationsService {

  private final OrganizationRepository organizationRepository;
  private final OrganizationUnitRepository organizationUnitRepository;
  private final OrganizationMapper mapper;

  @PersistenceContext private EntityManager entityManager;

  public OrganizationsService(
      OrganizationRepository organizationRepository,
      OrganizationUnitRepository organizationUnitRepository,
      OrganizationMapper mapper) {
    this.organizationRepository = organizationRepository;
    this.organizationUnitRepository = organizationUnitRepository;
    this.mapper = mapper;
  }

  @Transactional
  public Map<String, Object> create(OrganizationRequest req) {
    if (req.name == null || req.name.isBlank()) {
      throw ApiException.badRequest("กรุณาระบุชื่อหน่วยงาน/องค์กร");
    }
    Organization org = new Organization();
    applyFields(org, req);
    org = organizationRepository.save(org);
    return mapper.toMap(org);
  }

  @Transactional(readOnly = true)
  public Organization findEntity(Integer id) {
    return organizationRepository.findById(id).orElseThrow(() -> ApiException.notFound("ไม่พบองค์กรในระบบ"));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> findOne(Integer id) {
    return mapper.toMap(findEntity(id));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findAll() {
    return organizationRepository.findAllByOrderByNameAsc().stream()
        .map(org -> mapper.toListItem(org, org.getUsers().size()))
        .toList();
  }

  @Transactional
  public Map<String, Object> update(Integer id, OrganizationRequest req) {
    Organization org = findEntity(id);
    applyFields(org, req);
    org = organizationRepository.save(org);
    return mapper.toMap(org);
  }

  // --- Organization Units ---

  @Transactional
  public Map<String, Object> createUnit(Integer orgId, OrganizationUnitRequest req) {
    Organization org = findEntity(orgId);
    OrganizationUnit unit = new OrganizationUnit();
    unit.setOrganization(org);
    applyUnitFields(unit, req);
    unit = organizationUnitRepository.save(unit);
    return mapper.toUnitMap(unit);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> findUnitsByOrg(Integer orgId) {
    return organizationUnitRepository.findByOrgIdOrderByCreatedAtAsc(orgId).stream()
        .map(mapper::toUnitMap)
        .toList();
  }

  @Transactional(readOnly = true)
  public OrganizationUnit findUnitEntity(Integer unitId) {
    return organizationUnitRepository.findById(unitId).orElse(null);
  }

  @Transactional
  public Map<String, Object> updateUnit(Integer unitId, OrganizationUnitRequest req) {
    OrganizationUnit unit =
        organizationUnitRepository.findById(unitId).orElseThrow(() -> ApiException.notFound("ไม่พบหน่วยงานย่อย"));
    applyUnitFields(unit, req);
    unit = organizationUnitRepository.save(unit);
    return mapper.toUnitMap(unit);
  }

  @Transactional
  public void removeUnit(Integer unitId) {
    organizationUnitRepository.deleteById(unitId);
  }

  /**
   * A lightweight annual carbon summary. Reads carbon_activity_logs and assessments by native
   * SQL rather than JPA entities, since those modules haven't been ported to Java yet — this
   * avoids taking on their full entity graph just for two aggregate numbers.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> getAnnualReport(Integer orgId) {
    Organization org = findEntity(orgId);

    int currentYear = Year.now().getValue();
    int baseYear = org.getBaseYear() != null ? org.getBaseYear() : currentYear - 1;

    double currentYearEmissions = sumEmissions(orgId, currentYear);
    double baseYearEmissions = sumEmissions(orgId, baseYear);

    double estimatedYearlyEmission =
        (org.getNumberOfEmployees() != null ? org.getNumberOfEmployees() : 10) * 1.8
            + (org.getTotalFloorArea() != null ? org.getTotalFloorArea() : 100) * 0.08;

    double finalBaseYearEmissions = baseYearEmissions > 0 ? baseYearEmissions : estimatedYearlyEmission;
    double finalCurrentYearEmissions = currentYearEmissions;
    if (finalCurrentYearEmissions <= 0) {
      double targetPercent = org.getTargetReductionPercent() != null ? org.getTargetReductionPercent() : 10;
      finalCurrentYearEmissions = finalBaseYearEmissions * (1 - targetPercent / 2 / 100);
    }

    double totalCarbonReduction = 0;
    if (finalBaseYearEmissions > 0 && finalCurrentYearEmissions > 0) {
      totalCarbonReduction = Math.max(0, finalBaseYearEmissions - finalCurrentYearEmissions);
    }

    Number approvedCount =
        (Number)
            entityManager
                .createNativeQuery("select count(*) from assessments where org_id = :orgId and status = 'APPROVED'")
                .setParameter("orgId", orgId)
                .getSingleResult();

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("organization", Map.of("id", org.getId(), "name", org.getName()));
    result.put("year", currentYear);
    result.put("total_carbon_reduction", Math.round(totalCarbonReduction * 100.0) / 100.0);
    result.put("assessments_completed", approvedCount.intValue());
    result.put("active_employees", org.getUsers().size());
    result.put("status", "Preliminary");
    return result;
  }

  private double sumEmissions(Integer orgId, int year) {
    Object total =
        entityManager
            .createNativeQuery("select sum(total_emission) from carbon_activity_logs where org_id = :orgId and year = :year")
            .setParameter("orgId", orgId)
            .setParameter("year", year)
            .getSingleResult();
    return total != null ? ((Number) total).doubleValue() : 0;
  }

  public String exportCsv() {
    StringBuilder csv = new StringBuilder("ID,Name,Tax ID,Status,Created At\n");
    for (Organization org : organizationRepository.findAllByOrderByNameAsc()) {
      csv.append('"').append(org.getId()).append("\",\"")
          .append(org.getName() == null ? "" : org.getName().replace("\"", "\"\""))
          .append("\",\"")
          .append(org.getTaxId() == null ? "" : org.getTaxId())
          .append("\",\"")
          .append(org.isActive() ? "Active" : "Inactive")
          .append("\",\"")
          .append(org.getCreatedAt())
          .append("\"\n");
    }
    return csv.toString();
  }

  private void applyFields(Organization org, OrganizationRequest req) {
    if (req.name != null) org.setName(req.name);
    if (req.tax_id != null) org.setTaxId(req.tax_id);
    if (req.industry_type != null) org.setIndustryType(req.industry_type);
    if (req.number_of_employees != null) org.setNumberOfEmployees(req.number_of_employees);
    if (req.total_floor_area != null) org.setTotalFloorArea(req.total_floor_area);
    if (req.working_hours_per_year != null) org.setWorkingHoursPerYear(req.working_hours_per_year);
    if (req.base_year != null) org.setBaseYear(req.base_year);
    if (req.target_reduction_percent != null) org.setTargetReductionPercent(req.target_reduction_percent);
    if (req.target_year != null) org.setTargetYear(req.target_year);
    if (req.industry_benchmark_value != null) org.setIndustryBenchmarkValue(req.industry_benchmark_value);
    if (req.carbon_standard != null) org.setCarbonStandard(req.carbon_standard);
    if (req.current_green_status != null) org.setCurrentGreenStatus(req.current_green_status);
    if (req.is_active != null) org.setActive(req.is_active);
  }

  private void applyUnitFields(OrganizationUnit unit, OrganizationUnitRequest req) {
    if (req.unit_name != null) unit.setUnitName(req.unit_name);
    if (req.unit_type != null) unit.setUnitType(req.unit_type);
    if (req.area != null) unit.setArea(req.area);
    if (req.parent_unit_id != null) {
      OrganizationUnit parent = organizationUnitRepository.findById(req.parent_unit_id).orElse(null);
      unit.setParentUnit(parent);
    }
  }
}
