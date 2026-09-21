package th.ac.rmutt.greensync.assessments;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, Integer> {

  Optional<Certificate> findByAssessmentId(Integer assessmentId);
}
