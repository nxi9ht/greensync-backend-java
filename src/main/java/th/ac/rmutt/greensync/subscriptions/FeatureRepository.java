package th.ac.rmutt.greensync.subscriptions;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureRepository extends JpaRepository<Feature, Integer> {

  List<Feature> findAllByOrderByFeatureNameAsc();

  List<Feature> findByIdIn(List<Integer> ids);
}
