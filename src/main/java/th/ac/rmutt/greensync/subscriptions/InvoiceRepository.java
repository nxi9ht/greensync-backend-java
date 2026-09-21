package th.ac.rmutt.greensync.subscriptions;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Integer> {

  List<Invoice> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @Query("select coalesce(sum(i.amount), 0) from Invoice i where i.status = :status")
  double sumAmountByStatus(@Param("status") String status);

  @Query("select coalesce(sum(i.amount), 0) from Invoice i where i.status = :status and i.createdAt >= :since")
  double sumAmountByStatusSince(@Param("status") String status, @Param("since") Instant since);

  @Query(
      "select function('to_char', i.createdAt, 'YYYY-MM') as month, coalesce(sum(i.amount), 0) as amount "
          + "from Invoice i where i.status = :status "
          + "group by function('to_char', i.createdAt, 'YYYY-MM') order by function('to_char', i.createdAt, 'YYYY-MM') asc")
  List<Object[]> monthlyRevenueTrend(@Param("status") String status);
}
