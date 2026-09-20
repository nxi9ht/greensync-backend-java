package th.ac.rmutt.greensync.assessments;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, Integer> {

  Optional<Assessment> findFirstByOrganizationIdAndStatus(Integer orgId, String status);

  List<Assessment> findTop50ByOrderBySubmittedAtDesc();

  List<Assessment> findTop50ByAssessorIdOrderBySubmittedAtDesc(Integer assessorUserId);

  List<Assessment> findTop50ByOrganizationIdOrderBySubmittedAtDesc(Integer orgId);

  Optional<Assessment> findByIdAndOrganizationId(Integer id, Integer orgId);

  long countByOrganizationIdAndStatus(Integer orgId, String status);
}
