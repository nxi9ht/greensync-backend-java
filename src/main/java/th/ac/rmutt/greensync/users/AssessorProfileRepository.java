package th.ac.rmutt.greensync.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessorProfileRepository extends JpaRepository<AssessorProfile, Integer> {

  Optional<AssessorProfile> findByUserId(Integer userId);

  long countByVerificationStatus(String verificationStatus);
}
