package th.ac.rmutt.greensync.carbonlogs;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmissionFactorRepository extends JpaRepository<EmissionFactor, Integer> {

  List<EmissionFactor> findAllByOrderByNameAsc();
}
