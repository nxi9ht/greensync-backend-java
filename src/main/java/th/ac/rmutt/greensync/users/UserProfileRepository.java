package th.ac.rmutt.greensync.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Integer> {

  Optional<UserProfile> findByUserId(Integer userId);
}
