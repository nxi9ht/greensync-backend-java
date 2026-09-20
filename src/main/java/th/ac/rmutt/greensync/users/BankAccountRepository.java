package th.ac.rmutt.greensync.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, Integer> {

  Optional<BankAccount> findFirstByUserId(Integer userId);
}
