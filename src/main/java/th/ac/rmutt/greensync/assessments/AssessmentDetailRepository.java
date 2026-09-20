package th.ac.rmutt.greensync.assessments;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentDetailRepository extends JpaRepository<AssessmentDetail, Integer> {

  List<AssessmentDetail> findByAssessmentId(Integer assessmentId);

  Optional<AssessmentDetail> findByAssessmentIdAndCriteriaId(Integer assessmentId, Integer criteriaId);
}
