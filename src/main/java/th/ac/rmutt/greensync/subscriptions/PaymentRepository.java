package th.ac.rmutt.greensync.subscriptions;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

  List<Payment> findByOrgIdOrderByPaidAtDescCreatedAtDesc(Integer orgId);

  Optional<Payment> findByInvoiceId(Integer invoiceId);
}
