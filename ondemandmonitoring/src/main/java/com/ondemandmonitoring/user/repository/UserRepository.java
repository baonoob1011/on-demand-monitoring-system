package com.ondemandmonitoring.user.repository;

import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.role.domain.RoleCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for the User aggregate. */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select u
            from User u
            join u.role role
            where (:search is null
                    or lower(u.fullName) like lower(concat('%', :search, '%'))
                    or lower(u.email) like lower(concat('%', :search, '%')))
              and (:role is null or role.code = :role)
              and (:active is null or u.isActive = :active)
              and (:emailVerified is null or u.emailVerified = :emailVerified)
            """)
    Page<User> findForManagement(
            @Param("search") String search,
            @Param("role") RoleCode role,
            @Param("active") Boolean active,
            @Param("emailVerified") Boolean emailVerified,
            Pageable pageable);
}
