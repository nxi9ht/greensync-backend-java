package th.ac.rmutt.greensync.subscriptions;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Integer> {

  List<Invoice> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
