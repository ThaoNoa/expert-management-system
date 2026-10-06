package com.npcore.ems.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    @Query("select u from User u where lower(u.username) = lower(?1) and u.deletedAt is null")
    Optional<User> findActiveByUsername(String username);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(?1) and u.deletedAt is null and (?2 is null or u.id <> ?2)")
    boolean emailTaken(String email, UUID excludeId);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(?1) and u.deletedAt is null")
    boolean usernameTaken(String username);

    @Query("select count(u) > 0 from User u join u.roles r where r.id = ?1")
    boolean existsByRoleId(UUID roleId);
}
