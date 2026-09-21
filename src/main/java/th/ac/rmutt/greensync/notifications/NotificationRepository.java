package th.ac.rmutt.greensync.notifications;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

  List<Notification> findByRecipientIdOrderByCreatedAtDesc(Integer recipientId, Pageable pageable);

  long countByRecipientIdAndIsReadFalse(Integer recipientId);

  List<Notification> findByRecipientIdAndIsReadFalse(Integer recipientId);

  @Query("select n from Notification n order by n.createdAt desc")
  List<Notification> findAllOrderByCreatedAtDesc(Pageable pageable);

  @Query("select n from Notification n where n.recipient.id = :recipientId and n.id = :id")
  java.util.Optional<Notification> findByIdAndRecipientId(@Param("id") Integer id, @Param("recipientId") Integer recipientId);

  @Modifying
  @Transactional
  @Query("update Notification n set n.isRead = true where n.recipient.id = :recipientId and n.isRead = false")
  void markAllAsReadForRecipient(@Param("recipientId") Integer recipientId);
}
