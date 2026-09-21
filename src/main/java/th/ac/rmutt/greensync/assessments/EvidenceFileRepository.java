package th.ac.rmutt.greensync.assessments;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvidenceFileRepository extends JpaRepository<EvidenceFile, Integer> {

  @Query(
      "select f from EvidenceFile f "
          + "left join fetch f.uploadedBy ub left join fetch ub.organization "
          + "left join fetch f.assessmentDetail ad left join fetch ad.assessment "
          + "order by f.uploadedAt desc")
  List<EvidenceFile> findAllWithRelations();
}
