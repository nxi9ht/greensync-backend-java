package th.ac.rmutt.greensync.gemini;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatLogRepository extends JpaRepository<ChatLog, Integer> {

  List<ChatLog> findByUserIdOrderByCreatedAtAsc(Integer userId);

  List<ChatLog> findBySessionIdAndUserIdOrderByCreatedAtAsc(Integer sessionId, Integer userId);

  List<ChatLog> findBySessionIdAndUserIdOrderByCreatedAtDesc(Integer sessionId, Integer userId, Pageable pageable);

  void deleteBySessionIdAndUserId(Integer sessionId, Integer userId);

  void deleteByUserId(Integer userId);

  void deleteByIdInAndUserId(List<Integer> ids, Integer userId);

  @Query(
      "select l.sessionId as sessionId, max(l.sessionTitle) as title, max(l.createdAt) as updatedAt "
          + "from ChatLog l where l.userId = :userId and l.sessionId is not null "
          + "group by l.sessionId order by max(l.createdAt) desc")
  List<SessionSummary> findSessionSummaries(@Param("userId") Integer userId);

  interface SessionSummary {
    Integer getSessionId();

    String getTitle();

    java.time.Instant getUpdatedAt();
  }
}
