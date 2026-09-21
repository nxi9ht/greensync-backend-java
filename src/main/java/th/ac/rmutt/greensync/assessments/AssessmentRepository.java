package th.ac.rmutt.greensync.assessments;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentRepository extends JpaRepository<Assessment, Integer> {

  Optional<Assessment> findFirstByOrganizationIdAndStatus(Integer orgId, String status);

  List<Assessment> findTop50ByOrderBySubmittedAtDesc();

  List<Assessment> findTop50ByAssessorIdOrderBySubmittedAtDesc(Integer assessorUserId);

  List<Assessment> findTop50ByOrganizationIdOrderBySubmittedAtDesc(Integer orgId);

  Optional<Assessment> findByIdAndOrganizationId(Integer id, Integer orgId);

  long countByOrganizationIdAndStatus(Integer orgId, String status);

  List<Assessment> findByOrganizationIdAndStatusOrderByUpdatedAtDesc(Integer orgId, String status);

  Optional<Assessment> findByIdAndOrganizationIdAndStatus(Integer id, Integer orgId, String status);

  boolean existsByAssessorIdAndOrganizationId(Integer assessorUserId, Integer orgId);

  List<Assessment> findTop100ByAssessorIdAndStatusInOrderByUpdatedAtDesc(Integer assessorUserId, List<String> statuses);

  List<Assessment> findByAssessorIdAndStatusIn(Integer assessorUserId, List<String> statuses);

  List<Assessment> findByAssessorIsNullAndStatusIn(List<String> statuses);

  /** The assessor's own pool (assigned to them, active) plus the unclaimed active pool. */
  @Query(
      "select a from Assessment a where "
          + "(a.assessor.id = :assessorUserId and a.status in :activeStatuses) "
          + "or (a.assessor is null and a.status in :unassignedStatuses) "
          + "order by a.createdAt desc")
  List<Assessment> findAssignablePool(
      @Param("assessorUserId") Integer assessorUserId,
      @Param("activeStatuses") List<String> activeStatuses,
      @Param("unassignedStatuses") List<String> unassignedStatuses);
}
