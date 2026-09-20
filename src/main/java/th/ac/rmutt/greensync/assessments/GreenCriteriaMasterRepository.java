package th.ac.rmutt.greensync.assessments;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GreenCriteriaMasterRepository extends JpaRepository<GreenCriteriaMaster, Integer> {

  @Query("select c from GreenCriteriaMaster c order by c.categoryNumber asc, c.criteriaCode asc")
  List<GreenCriteriaMaster> findAllOrdered();
}
