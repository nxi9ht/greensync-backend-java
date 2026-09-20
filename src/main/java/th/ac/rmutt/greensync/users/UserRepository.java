package th.ac.rmutt.greensync.users;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Integer> {

  Optional<User> findByEmailIgnoreCase(String email);

  Optional<User> findByResetPasswordToken(String token);

  // roleName is passed pre-uppercased so Postgres never needs to infer a type for
  // upper(:roleName) itself — with a literal null parameter it wrongly infers "bytea"
  // and fails ("function upper(bytea) does not exist"), even though the null branch
  // means the value is never actually used.
  @Query(
      "select u from User u where (:orgId is null or u.organization.id = :orgId) "
          + "and (:roleName is null or exists (select r from u.roles r where upper(r.roleName) = :roleName)) "
          + "order by u.createdAt desc")
  List<User> search(@Param("orgId") Integer orgId, @Param("roleName") String roleName, Pageable pageable);

  @Query(
      "select u from User u where (:orgId is null or u.organization.id = :orgId) "
          + "and exists (select r from u.roles r where upper(r.roleName) in :roleNames) "
          + "order by u.createdAt desc")
  List<User> searchByAnyRole(
      @Param("orgId") Integer orgId, @Param("roleNames") List<String> roleNames, Pageable pageable);
}
